using System;
using System.Collections;
using System.Collections.Generic;
using DefaultNamespace;
using UnityEngine;

public static class ANRSupervisor
{
	public readonly struct BlockedMTEvent
	{
		public readonly int blockedTimeS;
		public readonly bool isForeground;

		public BlockedMTEvent(int blockedTimeS, bool isForeground)
		{
			this.blockedTimeS = blockedTimeS;
			this.isForeground = isForeground;
		}
	}
	
	public readonly struct ANREvent
	{
		public readonly string report;
		public readonly int blockedTimeS;
		public readonly bool isForeground;

		public ANREvent(string report, int blockedTimeS, bool isForeground)
		{
			this.report = report;
			this.blockedTimeS = blockedTimeS;
			this.isForeground = isForeground;
		}
	}
	
	public static event Action<ANREvent> onAnr;
	public static event Action<BlockedMTEvent> onMainThreadBlocked;
	public static event Action<string> onInfo;

	private static bool initialized = false;
	private static AndroidJavaClass anrSupervisor;

	public static void Start()
	{
#if UNITY_ANDROID && !UNITY_EDITOR
		Init();
		anrSupervisor?.CallStatic("start");
#endif
	}

	public static void Stop()
	{
#if UNITY_ANDROID && !UNITY_EDITOR
		Init();
		anrSupervisor?.CallStatic("stop");
#endif
	}

	public static void GenerateAnr()
	{
#if UNITY_ANDROID && !UNITY_EDITOR
		Init();
		anrSupervisor?.CallStatic("generateANROnMainThreadTEST");
#endif
	}

	private static void Init()
	{
		if (initialized)
			return;

		initialized = true;
#if UNITY_ANDROID && !UNITY_EDITOR
		anrSupervisor = new AndroidJavaClass("ANRSupervisor");
		using var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
		using var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
		using var context = activity.Call<AndroidJavaObject>("getApplicationContext");

		var callback = new ANRCallback(HandleAnr, HandleMainThreadBlocked, HandleInfo);
		anrSupervisor?.CallStatic("create", context, callback);
#endif
	}

	private static void HandleAnr(int blockedTimeS, string report)
	{
		onAnr?.Invoke(new ANREvent(report, blockedTimeS, Application.isFocused));
	}

	private static void HandleMainThreadBlocked(int blockedTimeS)
	{
		onMainThreadBlocked?.Invoke(new BlockedMTEvent(blockedTimeS, Application.isFocused));
	}
	
	private static void HandleInfo(string info)
	{
		onInfo?.Invoke(info);
	}

	public class ANRCallback : AndroidJavaProxy
	{
		private Action<int, string> anr;
		private Action<int> mainBlocked;
		private Action<string> infoHandler;
		
		public ANRCallback(Action<int, string> handler, Action<int> mainBlockedHandler, Action<string> infoHandler) : base("com.unity3d.player.IAnrCallback")
		{
			anr = handler;
			mainBlocked = mainBlockedHandler;
			this.infoHandler = infoHandler;
		}

		public void anrHandler(int blockedTimeS, string report)
		{
			Debug.Log($"[ANRSupervisor] Anr detected: {report}");
			anr?.Invoke(blockedTimeS, report);
		}

		public void mainBlockedHandler(int blockedTimeS)
		{
			Debug.Log($"[ANRSupervisor] Main thread is blocked for {blockedTimeS} seconds");
			mainBlocked?.Invoke(blockedTimeS);
		}


		public void info(string infoStr)
		{
			Debug.Log($"[ANRSupervisor] Info: {infoStr}");
			infoHandler?.Invoke(infoStr);
		}
	}
}
