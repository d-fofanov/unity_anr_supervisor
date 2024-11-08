using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public static class ANRSupervisor
{
	public static event Action<string> onAnr;

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
		var callback = new ANRCallback(HandleAnr);
		anrSupervisor?.CallStatic("create", callback);
#endif
	}

	private static void HandleAnr(string report)
	{
		onAnr?.Invoke(report);
	}


	public class ANRCallback : AndroidJavaProxy
	{
		private Action<string> handler;
		public ANRCallback(Action<string> handler) : base("com.unity3d.player.IAnrCallback")
		{
			this.handler = handler;
		}

		public void anrHandler(string report)
		{
			Debug.Log($"[ANRSupervisor] Anr detected: {report}");
			handler?.Invoke(report);
		}
	}
}
