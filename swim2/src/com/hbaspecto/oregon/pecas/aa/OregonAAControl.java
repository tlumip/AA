/**
 * 
 */
package com.hbaspecto.oregon.pecas.aa;

import java.util.ResourceBundle;

import com.hbaspecto.pecas.aa.control.AAControl;

/**
 * @author jabraham
 * Simply a way to see the .main method in the superclass.
 *
 */
public class OregonAAControl extends AAControl {

	public OregonAAControl(Class pProcessorClass, ResourceBundle aaRb,
			int baseYear, int timePeriod) {
		super(pProcessorClass, aaRb, baseYear, timePeriod);
	}
	
    public static void main(String[] args) {
    	AAControl.main(args);
    }

}
