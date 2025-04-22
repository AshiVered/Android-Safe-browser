package aiv.ashivered.safebrowser;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("גרסה 1.0\n\nדפדפן בטוח מבית Ashivered.");
        tv.setTextSize(18);
        int padding = (int)(16 * getResources().getDisplayMetrics().density);
        tv.setPadding(padding, padding, padding, padding);
        setContentView(tv);
    }
}
