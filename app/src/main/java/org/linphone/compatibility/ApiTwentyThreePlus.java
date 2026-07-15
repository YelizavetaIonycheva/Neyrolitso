package org.linphone.compatibility;

import android.widget.TextView;
import android.annotation.TargetApi;

@TargetApi(23)
public class ApiTwentyThreePlus {
	public static void setTextAppearance(TextView textview, int style) {
		textview.setTextAppearance(style);
	}
}
