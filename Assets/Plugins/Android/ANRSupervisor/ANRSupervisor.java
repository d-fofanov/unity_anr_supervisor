import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import com.unity3d.player.IAnrCallback;
import com.unity3d.player.UnityPlayer;

// A class supervising the UI thread for ANR errors. Use 
// {@link #start()} and {@link #stop()} to control
// when the UI thread is supervised
public class ANRSupervisor
{
	static ANRSupervisor instance;

	public static Logger logger = Logger.getLogger("ANRSupervisor");
	public static void Log(Object log) { logger.log(Level.INFO, "[ANRSupervisor] " + log); }
	public static String version;

	// The {@link ExecutorService} checking the UI thread
	private ExecutorService mExecutor;

	// The {@link ANRSupervisorRunnable} running on a separate thread
	public final ANRSupervisorRunnable mSupervisorRunnable;

	public ANRSupervisor(Looper looper, int timeoutCheckDuration, int checkInterval, IAnrCallback callback)
	{
		mExecutor = Executors.newSingleThreadExecutor();
		mSupervisorRunnable = new ANRSupervisorRunnable(looper, timeoutCheckDuration, checkInterval, callback);
	}
	
	public static void create(Context context, IAnrCallback callback) throws Exception
	{
		if (instance == null)
		{
			// Check for misbehaving SDKs on the main thread.
			ANRSupervisor.Log("Creating Main Thread Supervisor");
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = pInfo.versionName;
			instance = new ANRSupervisor(Looper.getMainLooper(), 4, 1, callback);
		}
	}

	// Starts the supervision
	public static synchronized void start()
	{
		synchronized (instance.mSupervisorRunnable)
		{
			ANRSupervisor.Log("Starting Supervisor");
			if (instance.mSupervisorRunnable.isStopped())
			{
				instance.mExecutor.execute(instance.mSupervisorRunnable);
			}
			else
			{
				instance.mSupervisorRunnable.resume();
			}
		}
	}

	// Stops the supervision. The stop is delayed, so if start() is called right after stop(),
	// both methods will have no effect. There will be at least one more ANR check before the supervision is stopped.
	public static synchronized void stop()
	{
		instance.mSupervisorRunnable.stop();
	}
	
	public static String getReport()
	{
		if (instance != null &&
			instance.mSupervisorRunnable != null &&
			instance.mSupervisorRunnable.mReport != null)
		{
			String report = instance.mSupervisorRunnable.mReport;
			instance.mSupervisorRunnable.mReport = null;
			return report;
		}
		return null;
	}
	
	public static void reportSent()
	{
		if (instance != null &&
			instance.mSupervisorRunnable != null)
		{
			instance.mSupervisorRunnable.mReportSent = true;
		}
	}

	public static synchronized void generateANROnMainThreadTEST()
	{
		ANRSupervisor.Log("Creating mutext locked infinite thread");
		new Thread(new Runnable() {
			@Override public void run() {
				synchronized (instance) {
					while (true) {
						ANRSupervisor.Log("Sleeping for 60 seconds");
						try {
							Thread.sleep(60*1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}).start();

		ANRSupervisor.Log("Running a callback on the main thread that tries to lock the mutex (but can't)");
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override public void run() {
				ANRSupervisor.Log("Trying to lock the mutex");
				synchronized (instance) {
					ANRSupervisor.Log("This shouldn't happen");
					throw new IllegalStateException();
				}
			}
		}, 1000);

		ANRSupervisor.Log("End of generateANROnMainThreadTEST");
	}
}

// A {@link Runnable} testing the UI thread every 5 seconds until {@link #stop()} is called
class ANRSupervisorRunnable implements Runnable
{
	// The {@link Handler} to access the UI threads message queue
	private Handler mHandler;

	// The stop flag
	private boolean mStopped;

	// Flag indicating the stop was performed
	private boolean mStopCompleted = true;

	private int mTimeoutCheck;
	private int mCheckInterval;
	private int mStatusUpdateInterval = 1;
	private int mMaxReportSendWaitDuration = 5;
	private IAnrCallback mCallback;
	
	public boolean mReportSent;
	public String mReport;

	public ANRSupervisorRunnable(Looper looper, int timeoutCheckDuration, int checkInterval, IAnrCallback callback)
    {
		ANRSupervisor.Log("Installing ANR Suparvisor on " + looper + " timeout: " + timeoutCheckDuration);
		mHandler = new Handler(looper);
		mTimeoutCheck = timeoutCheckDuration;
		mCheckInterval = checkInterval;
        mCallback = callback;
	}

	@Override public void run()
	{
		this.mStopCompleted = false;

		// Loop until stop() was called or thread is interrupted
		while (!Thread.interrupted())
		{
			try
			{
				//ANRSupervisor.Log("Sleeping for " + mCheckInterval + " seconds until next test");
				Thread.sleep(mCheckInterval * 1000);

				ANRSupervisor.Log("Check for ANR...");

				// Create new callback
				ANRSupervisorCallback callback = new ANRSupervisorCallback();

				// Perform test, Handler should run the callback within X seconds
				synchronized (callback)
				{
					this.mHandler.post(callback);
					int delay = 0;
					boolean anrSignalled = false;
                    while (true)
                    {
                        callback.wait(mStatusUpdateInterval * 1000);
                        
                        if (callback.isCalled())
                            break;
                            
                        delay++;
                        handleBlockedHandler(delay);
                        if (delay >= mTimeoutCheck && !anrSignalled)
                        {
                            anrSignalled = true;
                            handleCheckTimeout(delay, callback);
                        }
                    }
				}

				// Check if stopped
				this.checkStopped();
			}
			catch (InterruptedException e)
			{
				ANRSupervisor.Log("Interruption caught.");
				break;
			}
		}

		// Set stop completed flag
		this.mStopCompleted = true;

		ANRSupervisor.Log("supervision stopped");
	}
	
	private void handleCheckTimeout(int blockedTimeS, ANRSupervisorCallback callback) {
        ANRSupervisor.Log("Thread " + this.mHandler.getLooper() + " DID NOT respond within " + mTimeoutCheck + " seconds");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);

        // Get all stack traces in the system
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        Locale l = Locale.getDefault();

        String deviceName = "";
        try
        {
            android.content.ContentResolver cr = com.unity3d.player.UnityPlayer.currentActivity.getApplicationContext().getContentResolver();
            deviceName = android.provider.Settings.Secure.getString(cr, "device_name");
            if (deviceName == null || deviceName.length() <= 0)
            {
                deviceName = android.provider.Settings.Secure.getString(cr, "bluetooth_name");
            }
        }
        catch (Exception e) {}

        ps.print(String.format(l, "{\"title\":\"ANR Report\",\"build_version\":\"%s\",\"device\":\"%s\",\"name\":\"%s\",\"callstacks\":[",
            ANRSupervisor.version, android.os.Build.FINGERPRINT, deviceName));

        Thread supervisedThread = this.mHandler.getLooper().getThread();
        boolean isFirstThread = true;
        boolean gmsThreadIsBlocked = false;
        for (Thread thread : stackTraces.keySet())
        {
            boolean isBlocked = thread.getState().equals("BLOCKED");
            if (thread == supervisedThread ||
                thread.getName().equals("main") ||
                thread.getName().equals("UnityMain") ||
                isBlocked)
            {
                if (isFirstThread) { isFirstThread = false; } else { ps.print(","); }
                ps.print(String.format(l, "{\"name\":\"%s\",\"state\":\"%s\"", thread.getName(), thread.getState()));

                if (thread == supervisedThread)
                {
                    ps.print(",\"supervised\":true");
                }

                StackTraceElement[] stack = stackTraces.get(thread);
                if (stack.length > 0)
                {
                    ps.print(",\"stack\":[");
                    boolean isFirstLine = true;
                    int numStackLines = Math.min(stack.length, 3);
                    for (int i = 0; i < numStackLines; ++i)
                    {
                        if (isFirstLine) { isFirstLine = false; } else { ps.print(","); }
                        StackTraceElement element = stack[i];
                        ps.print(String.format(l, "{\"func\":\"%s.%s\",\"file\":\"%s\",\"line\":%d}",
                                element.getClassName(),
                                element.getMethodName(),
                                element.getFileName(), 
                                element.getLineNumber()));

                        if (isBlocked && element.getClassName().contains("gms.ads"))
                        {
                            gmsThreadIsBlocked = true;
                        }
                    }
                    ps.print("]");
                }
                ps.print("}");
            }
        }
        ps.print("]}");

        ANRSupervisor.Log("Checking for false-positive");
        if (!callback.isCalled())
        {
            String report = new String(bos.toByteArray());
            ANRSupervisor.Log(report);

            handleAnr(blockedTimeS, report);
        }
	}
	
	private void handleBlockedHandler(int blockedTimeS) {
        try {
            mCallback.mainBlockedHandler(blockedTimeS);
        }
        catch (Throwable t) {
        }
    }
	
	private void handleAnr(int blockedTimeS, String report) {
        try {
            mCallback.anrHandler(blockedTimeS, report);
        }
        catch (Throwable t) {
        }
	}

	private synchronized void checkStopped() throws InterruptedException
	{
		if (this.mStopped)
		{
			// Wait 1 second
			Thread.sleep(1000);

			// Break if still stopped
			if (this.mStopped)
			{
				throw new InterruptedException();
			}
		}
	}

	synchronized void stop()
	{
		ANRSupervisor.Log("Stopping...");
		this.mStopped = true;
	}

	synchronized void resume()
	{
		ANRSupervisor.Log("Resuming...");
		this.mStopped = false;
	}

	synchronized boolean isStopped() { return this.mStopCompleted; }
}

// A {@link Runnable} which calls {@link #notifyAll()} when run.
class ANRSupervisorCallback implements Runnable
{
	private boolean mCalled;

	public ANRSupervisorCallback() { super(); }

	@Override public synchronized void run()
	{
		this.mCalled = true;
		this.notifyAll();
	}

	synchronized boolean isCalled() { return this.mCalled; }
}
//*/
