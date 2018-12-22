package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.R;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class FileListAdapter extends ArrayAdapter<FileWrapper> {
	private static final String TAG = "HedgeCam/FileListAdapter";

	private final Context context;
	private final int layoutId;
	private final List<FileWrapper> list;

	public FileListAdapter(final Context context, final int layoutId, final List<FileWrapper> list) {
		super(context, layoutId, list);
		this.context = context;
		this.layoutId = layoutId;
		this.list = list;
	}
	
	@Override
	public View getView(final int position, View convertView, final ViewGroup parent) {
		if( MyDebug.LOG )
			Log.d(TAG, "getView, position: " + position);
		
		if (convertView == null) {
			convertView = LayoutInflater.from(context).inflate(layoutId, parent, false);
		}
		FileWrapper item = list.get(position);
		String icon;
		int color = Color.WHITE;
		switch (item.getItemType()) {
			case FileWrapper.ITEM_LEVEL_UP:
				icon = IconView.UP;
				break;
			case FileWrapper.ITEM_FOLDER:
				icon = IconView.FOLDER;
				color = context.getResources().getColor(R.color.folder);
				break;
			default:
				icon = IconView.FILE;
		}
		
		IconView iconView = (IconView)convertView.findViewById(R.id.icon);
		iconView.setDrawShadow(false);
		iconView.setText(icon);
		iconView.setTextColor(color);	
		((TextView)convertView.findViewById(R.id.text)).setText(item.toString());
		convertView.setBackgroundColor(item.getSelected() ? 0x7f7f7f7f : 0x00000000);
		return convertView;
	}

	public void update() {
		notifyDataSetChanged();
	}
}
