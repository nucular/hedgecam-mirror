package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.MainActivity;
import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.R;
import com.caddish_hedgehog.hedgecam2.StorageUtils;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

/** Dialog to pick a folder. Also allows creating new folders. Used when not
 *  using the Storage Access Framework.
 */
public class FileListDialog extends DialogFragment {
	private static final String TAG = "HedgeCam/FileListDialog";
	
	public static abstract class Listener {
		public void onSelected(String file) {};
		public void onCancelled() {};
	}

	private final Listener listener;
	private File current_folder;
	private AlertDialog folder_dialog;
	private ListView list;
	private String[] extensions = null;
	private final boolean multi_select;
	private String pref_key = Prefs.SAVE_LOCATION;

	public FileListDialog(String pref_key, Listener listener) {
		super();
		if (pref_key != null)
			this.pref_key = pref_key;
		this.listener = listener;
		this.multi_select = false;
	}

	public FileListDialog(String[] extensions, Listener listener) {
		super();
		this.listener = listener;
		this.extensions = extensions;
		Arrays.sort(this.extensions);
		this.multi_select = false;
	}

	public FileListDialog(String[] extensions, boolean multi_select, Listener listener) {
		super();
		this.listener = listener;
		this.extensions = extensions;
		Arrays.sort(this.extensions);
		this.multi_select = multi_select;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreateDialog");
		String folder_name = Prefs.getString(pref_key, "HedgeCam");
		if( MyDebug.LOG )
			Log.d(TAG, "folder_name: " + folder_name);
		File new_folder = StorageUtils.getImageFolder(folder_name);
		if( MyDebug.LOG )
			Log.d(TAG, "start in folder: " + new_folder);

		list = new ListView(getActivity());
		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if( MyDebug.LOG )
					Log.d(TAG, "onItemClick: " + position);
				FileWrapper file_wrapper = (FileWrapper) parent.getItemAtPosition(position);
				if( MyDebug.LOG )
					Log.d(TAG, "clicked: " + file_wrapper.toString());
				File file = file_wrapper.getFile();
				if( MyDebug.LOG )
					Log.d(TAG, "file: " + file.toString());
				if (file.isDirectory())
					refreshList(file);
				else if (file.isFile()) {
					if (multi_select) {
						file_wrapper.reverseSelected();
						((FileListAdapter)list.getAdapter()).update();
					} else {
						folder_dialog.dismiss();
						if (listener != null)
							listener.onSelected(file.getAbsolutePath());
					}
				}
			}
		});
		// good to use as short a text as possible for the icons, to reduce chance that the three buttons will have to appear on top of each other rather than in a row, in portrait mode
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
			//.setIcon(R.drawable.alert_dialog_icon)
			.setView(list)
			.setNegativeButton(android.R.string.cancel, null);
		if (extensions == null || multi_select)
			builder.setPositiveButton(android.R.string.ok, null); // we set the listener in onShowListener, so we can prevent the dialog from closing (if chosen folder isn't writable)
		if (extensions == null)
			builder.setNeutralButton(R.string.new_folder, null); // we set the listener in onShowListener, so we can prevent the dialog from closing

		folder_dialog = builder.create();
		folder_dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog_interface) {
				if (multi_select) {
					Button b_positive = folder_dialog.getButton(AlertDialog.BUTTON_POSITIVE);
					b_positive.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							if( MyDebug.LOG )
								Log.d(TAG, "choose files from folder: " + current_folder.toString());
							// Finish it!
						}
					});
				} else if (extensions == null) {
					Button b_positive = folder_dialog.getButton(AlertDialog.BUTTON_POSITIVE);
					b_positive.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							if( MyDebug.LOG )
								Log.d(TAG, "choose folder: " + current_folder.toString());
							String folder = useFolder();
							if( folder != null ) {
								folder_dialog.dismiss();
								if (listener != null)
									listener.onSelected(folder);
							}
						}
					});

					Button b_neutral = folder_dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
					b_neutral.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							if( MyDebug.LOG )
								Log.d(TAG, "new folder in: " + current_folder.toString());
							newFolder();
						}
					});
				}

				Button b_negative = folder_dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
				b_negative.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						folder_dialog.dismiss();
						if (listener != null)
							listener.onCancelled();
					}
				});
			}
		});

		if( !new_folder.exists() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "create new folder" + new_folder);
			if( !new_folder.mkdirs() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "failed to create new folder");
				// don't do anything yet, this is handled below
			}
		}
		refreshList(new_folder);
/*		if( !canWrite() ) {
			// see testFolderChooserInvalid()
			if( MyDebug.LOG )
				Log.d(TAG, "failed to read folder");
			// note that we reset to DCIM rather than DCIM/OpenCamera, just to increase likelihood of getting back to a valid state
			refreshList(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
			if( current_folder == null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "can't even read DCIM?!");
				refreshList(new File("/"));
			}
		}*/
		return folder_dialog;
	}
	
	private void refreshList(File new_folder) {
		if( MyDebug.LOG )
			Log.d(TAG, "refreshList: " + new_folder);
		if( new_folder == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "refreshList: null folder");
			return;
		}
		File [] files = null;
		// try/catch just in case?
		try {
			files = new_folder.listFiles();
		}
		catch(Exception e) {
			if( MyDebug.LOG )
				Log.d(TAG, "exception reading folder");
			e.printStackTrace();
		}
		// n.b., files may be null if no files could be found in the folder (or we can't read) - but should still allow the user
		// to view this folder (so the user can go to parent folders which might be readable again)
		List<FileWrapper> listed_files = new ArrayList<>();
		if( new_folder.getParentFile() != null )
			listed_files.add(new FileWrapper(new_folder.getParentFile(), FileWrapper.ITEM_LEVEL_UP));
//		File default_folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
//		if( !default_folder.equals(new_folder) && !default_folder.equals(new_folder.getParentFile()) )
//			listed_files.add(new FileWrapper(default_folder, null, 1));
		if( files != null ) {
			for(File file : files) {
				if (file.isDirectory()) {
					listed_files.add(new FileWrapper(file, FileWrapper.ITEM_FOLDER));
				} else if ( extensions != null && file.isFile()) {
					String name = file.getName();
					String ext = null;
					int dot_pos = name.lastIndexOf('.');
					if (dot_pos > 0)
						ext = name.substring(dot_pos+1).toLowerCase();
						
					if (ext != null && Arrays.binarySearch(extensions, ext) >= 0)
						listed_files.add(new FileWrapper(file, FileWrapper.ITEM_FILE));
				}
			}
		}
		if ((files == null || files.length == 0) && new_folder.getAbsolutePath().equals("/storage/emulated")) {
			listed_files.add(new FileWrapper(new File("/storage/emulated/0"), FileWrapper.ITEM_FOLDER));
		}
		Collections.sort(listed_files);

		FileListAdapter adapter = new FileListAdapter(this.getActivity(), R.layout.file_list_item, listed_files);
		list.setAdapter(adapter);

		this.current_folder = new_folder;
		//dialog.setTitle(current_folder.getName());
		folder_dialog.setTitle(current_folder.getAbsolutePath());
	}
	
	private boolean canWrite() {
		try {
			if( this.current_folder != null && this.current_folder.canWrite() )
				return true;
		}
		catch(Exception e) {
			if( MyDebug.LOG )
				Log.d(TAG, "exception in canWrite()");
		}
		return false;
	}

	private String useFolder() {
		if( MyDebug.LOG )
			Log.d(TAG, "useFolder");
		if( current_folder == null )
			return null;
		if( canWrite() ) {
			File base_folder = StorageUtils.getBaseFolder();
			String new_save_location = current_folder.getAbsolutePath();
			if( current_folder.getParentFile() != null && current_folder.getParentFile().equals(base_folder) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "parent folder is base folder");
				new_save_location = current_folder.getName();
			}
			if( MyDebug.LOG )
				Log.d(TAG, "new_save_location: " + new_save_location);
			return new_save_location;
		}
		else {
			Toast.makeText(getActivity(), R.string.cant_write_folder, Toast.LENGTH_SHORT).show();
		}
		return null;
	}
	
	private static class NewFolderInputFilter implements InputFilter {
		// whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
		private final static String disallowed = "|\\?*<\":>";
		
		@Override
		public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
			for(int i=start;i<end;i++) {
				if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
					return "";
				}
			}
			return null;
		}
	}
	
	private void newFolder() {
		if( MyDebug.LOG )
			Log.d(TAG, "newFolder");
		if( current_folder == null )
			return;
		if( canWrite() ) {
			final EditText edit_text = new EditText(getActivity());
			edit_text.setSingleLine();
			InputFilter filter = new NewFolderInputFilter();
			edit_text.setFilters(new InputFilter[]{filter});

			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
				//.setIcon(R.drawable.alert_dialog_icon)
				.setTitle(R.string.enter_new_folder)
				.setView(edit_text)
				.setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if( edit_text.getText().length() == 0 ) {
							// do nothing
						}
						else {
							try {
								String new_folder_name = current_folder.getAbsolutePath() + File.separator + edit_text.getText().toString();
								if( MyDebug.LOG )
									Log.d(TAG, "create new folder: " + new_folder_name);
								File new_folder = new File(new_folder_name);
								if( new_folder.exists() ) {
									if( MyDebug.LOG )
										Log.d(TAG, "folder already exists");
									Toast.makeText(getActivity(), R.string.folder_exists, Toast.LENGTH_SHORT).show();
								}
								else if( new_folder.mkdirs() ) {
									if( MyDebug.LOG )
										Log.d(TAG, "created new folder");
									refreshList(new_folder);
								}
								else {
									if( MyDebug.LOG )
										Log.d(TAG, "failed to create new folder");
									Toast.makeText(getActivity(), R.string.failed_create_folder, Toast.LENGTH_SHORT).show();
								}
							}
							catch(Exception e) {
								if( MyDebug.LOG )
									Log.d(TAG, "exception trying to create new folder");
								e.printStackTrace();
								Toast.makeText(getActivity(), R.string.failed_create_folder, Toast.LENGTH_SHORT).show();
							}
						}
						
//						((Dialog)dialog).getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
					}
				})
				.setNegativeButton(android.R.string.cancel, null);

			builder.show().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		}
		else {
			Toast.makeText(getActivity(), R.string.cant_write_folder, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		// refresh in case files have changed
		refreshList(current_folder);
	}
}
