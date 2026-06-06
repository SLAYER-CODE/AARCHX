package org.aarchdroid.andraxdialogs;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import java.util.Objects;
import org.aarchdroid.R;

/* JADX INFO: loaded from: classes2.dex */
public class Alert extends Activity {
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        requestWindowFeature(1);
        super.onCreate(bundle);
        if (getIntent().hasExtra("icon")) {
            setContentView(R.layout.andrax_alert_dialog);
            setFinishOnTouchOutside(false);
            Button button = (Button) findViewById(R.id.button_alert_dialog_ok);
            Button button2 = (Button) findViewById(R.id.button_alert_dialog_cancel);
            ImageView imageView = (ImageView) findViewById(R.id.imageView_alert_dialog_icon);
            if (!Objects.equals(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("icon"), "notificationoff")) {
                if (!Objects.equals(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("icon"), "afos-ng")) {
                    if (!Objects.equals(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("icon"), "afos-ng-not-found")) {
                        if (!Objects.equals(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("icon"), "error")) {
                            if (!Objects.equals(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("icon"), "no-windows")) {
                                if (Objects.equals(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("icon"), "no-macos")) {
                                    imageView.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.no_macos));
                                }
                            } else {
                                imageView.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.no_windows));
                            }
                        } else {
                            imageView.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.fatal_error));
                        }
                    } else {
                        imageView.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.afos_ng_not_found));
                    }
                } else {
                    imageView.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.afos_ng));
                }
            } else {
                imageView.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.notification_is_off));
            }
            TextView textView = (TextView) findViewById(R.id.textview_alert_dialog_title);
            TextView textView2 = (TextView) findViewById(R.id.textview_alert_dialog_subtitle);
            TextView textView3 = (TextView) findViewById(R.id.textview_alert_dialog_content);
            textView.setText(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("title"));
            textView2.setText(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("subtitle"));
            textView3.setText(((Bundle) Objects.requireNonNull(getIntent().getExtras())).getString("content"));
            if (((Bundle) Objects.requireNonNull(getIntent().getExtras())).getBoolean("ok_button")) {
                button.setVisibility(0);
                button.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andraxdialogs.Alert.1
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        Alert alert = Alert.this;
                        alert.setResult(-1, alert.getIntent());
                        Alert.this.finish();
                    }
                });
            } else {
                button.setVisibility(8);
            }
            if (((Bundle) Objects.requireNonNull(getIntent().getExtras())).getBoolean("cancel_button")) {
                button2.setVisibility(0);
                button2.setOnClickListener(new View.OnClickListener() { // from class: org.snakesecurity.andraxdialogs.Alert.2
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        Alert alert = Alert.this;
                        alert.setResult(0, alert.getIntent());
                        Alert.this.finish();
                    }
                });
                return;
            } else {
                button2.setVisibility(8);
                return;
            }
        }
        setResult(0, getIntent());
        finish();
    }

    @Override // android.app.Activity
    public void onPause() {
        super.onPause();
    }
}
