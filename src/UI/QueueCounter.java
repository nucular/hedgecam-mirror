package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.R;
import android.view.View;
import android.widget.TextView;
import android.os.Handler;
import android.os.Message;

public class QueueCounter {
	private static final String TAG = "HedgeCam/QueueCounter";
	
	private static final int MSG_RESET = 1;
	private static final int MSG_INCREASE = 2;
	private static final int MSG_DECREASE = 3;
	
	private final TextView view;
	private final Handler handler;

	public QueueCounter(TextView v) {
		view = v;
		handler = new Handler() {
			private int counter = 0;
			@Override
			public void handleMessage(Message message) {
				switch (message.what) {
					case MSG_RESET:
						counter = 0;
						if (view.getVisibility() == View.VISIBLE) {
							view.setText("");
							view.setVisibility(View.GONE);
						}
						break;
					case MSG_INCREASE:
						counter++;
						if (counter > 1) {
							view.setText(Integer.toString(counter-1));
							if (view.getVisibility() != View.VISIBLE);
								view.setVisibility(View.VISIBLE);
						}
						break;
					case MSG_DECREASE:
						if (counter > 0) {
							counter--;
							if (counter > 1)
								view.setText(Integer.toString(counter-1));
							else if (view.getVisibility() == View.VISIBLE) {
								view.setText("");
								view.setVisibility(View.GONE);
							}
						}
						break;
				}
			}
		};
	}

	public void reset() {
		handler.sendEmptyMessage(MSG_RESET);
	}

	public void increase() {
		handler.sendEmptyMessage(MSG_INCREASE);
	}

	public void decrease() {
		handler.sendEmptyMessage(MSG_DECREASE);
	}
}
