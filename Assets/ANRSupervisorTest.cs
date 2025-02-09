using System.Collections;
using System.Collections.Generic;
using DefaultNamespace;
using UnityEngine;

public class ANRSupervisorTest : MonoBehaviour
{
	private string reportText = "no anr";
	private string infoText = "";
	private string fgText = "";
	private string blockedTimeText = "0";

	void Start()
	{
		ANRSupervisor.onAnr += (report) => reportText = $"Blocked: {report.blockedTimeS}, fg = {report.isForeground}\n{report.report}";
		ANRSupervisor.onInfo += (info) => infoText += $"{info}, fg = {Application.isFocused}\n";
		ANRSupervisor.onMainThreadBlocked += (evt) => blockedTimeText = $"Fg = {evt.isForeground}, blocked: {evt.blockedTimeS}";
	}
	
    void OnGUI()
    {
	    GUI.TextArea(new Rect(250, 100, 300, 800), infoText);
	    GUI.TextArea(new Rect(550, 100, 300, 800), reportText);
	    
        if (GUI.Button(new Rect(100, 100, 100, 40), "Start")) {
			ANRSupervisor.Start();
		}

        if (GUI.Button(new Rect(100, 160, 100, 40), "Stop")) {
			ANRSupervisor.Stop();
		}

        if (GUI.Button(new Rect(100, 220, 100, 40), "GenANR")) {
			ANRSupervisor.GenerateAnr();
		}
        
	    GUI.Label(new Rect(100, 280, 100, 40), $"Blocked time: {blockedTimeText}");
	    
	    GUI.TextArea(new Rect(0, 350, 250, 500), fgText);
    }
}
