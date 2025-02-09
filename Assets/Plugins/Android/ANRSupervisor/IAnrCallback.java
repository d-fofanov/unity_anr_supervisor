package com.unity3d.player;

public interface IAnrCallback {
	void anrHandler(int blockedTimeS, String report);
	void mainBlockedHandler(int blockedTimeS);
	void info(String info);
}
