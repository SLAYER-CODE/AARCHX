package org.aarchdroid;

import android.os.Bundle;
import androidx.cardview.widget.CardView;
import android.view.View;

import android.view.Window;
import android.util.Log;

public class Dco_voip_3g_4g extends DcoBaseActivity {
    private static final String TAG = "Dco_voip_3g_4g";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.dco_voip_3g_4g);

        CardView cardviewsipsak = findViewById(R.id.card_view_sipsak);
        CardView cardviewenodeb = findViewById(R.id.card_view_enodebhack);
        CardView cardviewmmeenodeb = findViewById(R.id.card_view_mmeenodebhack);
        CardView cardviewpgw = findViewById(R.id.card_view_pgwhack);
        CardView cardviewdiameterenum = findViewById(R.id.card_view_diameterenum);
        CardView cardviews1apenum = findViewById(R.id.card_view_s1apenum);
        CardView cardviewgtpscan = findViewById(R.id.card_view_gtpscan);
        CardView cardviewsgw = findViewById(R.id.card_view_sgwhack);
        CardView cardviewcryptomobile = findViewById(R.id.card_view_cryptomobile);
        CardView cardviewenumiax = findViewById(R.id.card_view_enumiax);
        CardView cardviewsvmap = findViewById(R.id.card_view_svmap);
        CardView cardviewisip = findViewById(R.id.card_view_isip);
        CardView cardviewvsaudit = findViewById(R.id.card_view_vsaudit);
        CardView cardviewprotostestsuite = findViewById(R.id.card_view_protostestsuite);
        CardView cardviewiaxflood = findViewById(R.id.card_view_iaxflood);
        CardView cardviewinviteflood = findViewById(R.id.card_view_inviteflood);
        CardView cardviewrtpflood = findViewById(R.id.card_view_rtpflood);
        CardView cardviewudpfloodVLAN = findViewById(R.id.card_view_udpfloodVLAN);
        CardView cardviewrtpbreak = findViewById(R.id.card_view_rtpbreak);
        CardView cardviewsipcracker = findViewById(R.id.card_view_sipcracker);
        CardView cardviewrtpinsertsound = findViewById(R.id.card_view_rtpinsertsound);

        cardviewenodeb.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("eNodeB-HACK -h");

            }
        });

        cardviewmmeenodeb.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("mme-eNodeB-HACK -h");

            }
        });

        cardviewpgw.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("PGW-HACK -h");

            }
        });

        cardviewdiameterenum.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("diameter_enum -h");

            }
        });

        cardviews1apenum.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("s1ap_enum");

            }
        });

        cardviewgtpscan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("gtp_scan -h");

            }
        });


        cardviewcryptomobile.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("cryptomobile");

            }
        });

        cardviewsgw.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("SGW-HACK -h");

            }
        });

        cardviewenumiax.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("enumiax");

            }
        });

        cardviewsvmap.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("sipvicious_svmap");

            }
        });

        cardviewisip.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("sudo isip");

            }
        });

        cardviewsipsak.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("sipsak");

            }
        });

        cardviewvsaudit.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("vsaudit");

            }
        });

        cardviewprotostestsuite.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("protos-test-suite");

            }
        });

        cardviewiaxflood.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("iaxflood");

            }
        });

        cardviewinviteflood.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("inviteflood");

            }
        });

        cardviewrtpflood.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("rtpflood");

            }
        });

        cardviewudpfloodVLAN.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("udpfloodVLAN");

            }
        });

        cardviewrtpbreak.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("rtpbreak");

            }
        });

        cardviewsipcracker.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("sipcracker");

            }
        });

        cardviewrtpinsertsound.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                run_hack_cmd("rtpinsertsound");

            }
        });

        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            finish();
        }
    }

    
@Override
    public void onPause() {

        super.onPause();
        Log.d(TAG, "onPause");
        finish();
    }

}
