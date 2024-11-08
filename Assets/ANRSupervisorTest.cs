using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class ANRSupervisorTest : MonoBehaviour
{
    void OnGUI()
    {
        if (GUI.Button(new Rect(100, 100, 100, 40), "Start")) {
			ANRSupervisor.Start();
		}

        if (GUI.Button(new Rect(100, 160, 100, 40), "Stop")) {
			ANRSupervisor.Stop();
		}

        if (GUI.Button(new Rect(100, 220, 100, 40), "GenANR")) {
			ANRSupervisor.GenerateAnr();
		}
    }
}
