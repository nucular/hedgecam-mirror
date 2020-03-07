package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.MainActivity;
import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.R;
import com.caddish_hedgehog.hedgecam2.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/** Dialog to pick a folder. Also allows creating new folders. Used when not
 *  using the Storage Access Framework.
 */
public class FileListDialog extends DialogFragment {
	private static final String TAG = "HedgeCam/FileListDialog";

	public static abstract class Listener {
		public void onSelected(String file) {};
		public void onSelected(Set<String> files) {};
		public void onCancelled() {};
	}

	private final Listener listener;
	private File current_folder;
	private AlertDialog file_list_dialog;
	private ListView list_view;
	private EditText file_name;
	private boolean check_writeable;
	private String[] extensions = null;
	private final boolean multi_select;
	private final int min_files;
	private final int max_files;
	private String start_folder;
	private String default_file_name;

	// Select folder
	public FileListDialog(String start_folder, boolean check_writeable, Listener listener) {
		super();
		this.start_folder = start_folder;
		this.listener = listener;
		this.multi_select = false;
		this.min_files = 0;
		this.max_files = 0;
		this.check_writeable = check_writeable;
	}

	// Save file
	public FileListDialog(String default_file_name, Listener listener) {
		super();
		this.listener = listener;
		this.multi_select = false;
		this.min_files = 0;
		this.max_files = 0;
		this.default_file_name = default_file_name;

		int dot_pos = default_file_name.lastIndexOf('.');
		if (dot_pos > 0) {
			this.extensions = new String[] {default_file_name.substring(dot_pos+1).toLowerCase()};
			Arrays.sort(this.extensions); // for binarySearch
		}
	}

	// Select file
	public FileListDialog(String[] extensions, Listener listener) {
		super();
		this.listener = listener;
		this.extensions = extensions;
		Arrays.sort(this.extensions); // for binarySearch
		this.multi_select = false;
		this.min_files = 0;
		this.max_files = 0;
	}

	// Select files
	public FileListDialog(String[] extensions, int min_files, int max_files, Listener listener) {
		super();
		this.listener = listener;
		this.extensions = extensions;
		Arrays.sort(this.extensions); // for binarySearch
		this.multi_select = true;
		this.min_files = min_files;
		this.max_files = max_files;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		String path = start_folder;
		if (path == null) {
			path = Prefs.getString(Prefs.LAST_FOLDER, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
		}
		File new_folder = new File(path);

		list_view = new ListView(getActivity());
		list_view.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				FileWrapper file_wrapper = (FileWrapper) parent.getItemAtPosition(position);
				File file = file_wrapper.getFile();
				if (file.isDirectory())
					refreshList(file);
				else if (file.isFile()) {
					if (file_name != null) {
						file_name.setText(file.getName());
					} else if (multi_select) {
						file_wrapper.reverseSelected();
						((FileListAdapter)list_view.getAdapter()).update();
						
						int selected = 0;
						for (FileWrapper item : ((FileListAdapter)list_view.getAdapter()).getList()) {
							if (item.getSelected()) {
								selected++;
							}
						}
						file_list_dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected >= min_files && (max_files < min_files || selected <= max_files));
					} else {
						file_list_dialog.dismiss();
						if (listener != null)
							listener.onSelected(file.getAbsolutePath());

						saveLastFolder();
					}
				}
			}
		});
		
		View root_view = list_view;
		if (default_file_name != null) {
			LinearLayout layout = new LinearLayout(this.getContext());
			layout.setOrientation(LinearLayout.VERTICAL);
			list_view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
			layout.addView(list_view);
			
			file_name = new EditText(getActivity());
			file_name.setSingleLine();
			file_name.setText(default_file_name);
			file_name.setFilters(new InputFilter[]{new FileNameInputFilter()});
			file_name.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
			layout.addView(file_name);
			
			root_view = layout;
		}

		// good to use as short a text as possible for the icons, to reduce chance that the three buttons will have to appear on top of each other rather than in a row, in portrait mode
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
			//.setIcon(R.drawable.alert_dialog_icon)
			.setView(root_view)
			.setNegativeButton(android.R.string.cancel, null);
		if (extensions == null || multi_select || file_name != null)
			builder.setPositiveButton(android.R.string.ok, null); // we set the listener in onShowListener, so we can prevent the dialog from closing (if chosen folder isn't writable)
		if (extensions == null)
			builder.setNeutralButton(R.string.new_folder, null); // we set the listener in onShowListener, so we can prevent the dialog from closing

		file_list_dialog = builder.create();
		file_list_dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog_interface) {
				if (file_name != null) {
					file_list_dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							String name = file_name.getText().toString();
							if (name.length() > 0) {
								if (canWrite()) {
									final File file = new File(current_folder.getAbsolutePath() + "/" + name);
									boolean exists = file.exists();
									if (!exists || file.isFile()) {
										if (exists) {
											AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
												.setMessage(R.string.overwrite_file_question)
												.setPositiveButton(R.string.answer_yes, new Dialog.OnClickListener() {
													@Override
													public void onClick(DialogInterface dialog, int which) {
														file_list_dialog.dismiss();
														if (listener != null) 
															listener.onSelected(file.getAbsolutePath());
														saveLastFolder();
													}
												})
												.setNegativeButton(android.R.string.cancel, null);

											builder.show();
										} else {
											file_list_dialog.dismiss();
											if (listener != null) 
												listener.onSelected(file.getAbsolutePath());
											saveLastFolder();
										}
										
									}
								} else
									Toast.makeText(getActivity(), R.string.cant_write_folder, Toast.LENGTH_SHORT).show();
							}
						}
					});
				} else if (multi_select) {
					Button positive = file_list_dialog.getButton(AlertDialog.BUTTON_POSITIVE);
					positive.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							if( MyDebug.LOG )
								Log.d(TAG, "choose files from folder: " + current_folder.toString());
								
							file_list_dialog.dismiss();
							
							if (listener != null) {
								Set<String> result = new TreeSet<String>();
								for (FileWrapper item : ((FileListAdapter)list_view.getAdapter()).getList()) {
									int type = item.getItemType();
									if (type != FileWrapper.ITEM_LEVEL_UP && type != FileWrapper.ITEM_FOLDER && item.getSelected())
										result.add(item.getFile().getAbsolutePath());
								}

								listener.onSelected(result);
							}

							saveLastFolder();
						}
					});
					positive.setEnabled(false);
				} else if (extensions == null) {
					file_list_dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							if( MyDebug.LOG )
								Log.d(TAG, "choose folder: " + current_folder.toString());
							if( current_folder != null ) {
								if(!check_writeable || canWrite()) {
									file_list_dialog.dismiss();
									if (listener != null)
										listener.onSelected(current_folder.getAbsolutePath());
								} else {
									Toast.makeText(getActivity(), R.string.cant_write_folder, Toast.LENGTH_SHORT).show();
								}
							} 
						}
					});

					file_list_dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							if( MyDebug.LOG )
								Log.d(TAG, "new folder in: " + current_folder.toString());
							newFolder();
						}
					});
				}

				file_list_dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						file_list_dialog.dismiss();
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

		return file_list_dialog;
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
		list_view.setAdapter(adapter);

		this.current_folder = new_folder;
		file_list_dialog.setTitle(current_folder.getAbsolutePath());
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

	private static class FileNameInputFilter implements InputFilter {
		// whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
		private final static String disallowed = "/|\\?*<\":>";
		
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
			InputFilter filter = new FileNameInputFilter();
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
	
	@Override
	public void onCancel(DialogInterface dialog_interface) {
		super.onCancel(dialog_interface);
		file_list_dialog.dismiss();
		if (listener != null)
			listener.onCancelled();
	}
	
	private void saveLastFolder() {
		Prefs.setString(Prefs.LAST_FOLDER, current_folder.getAbsolutePath());
	}
}

class FileWrapper implements Comparable<FileWrapper> {
	public static final int ITEM_LEVEL_UP = 0;
	public static final int ITEM_FOLDER = 1;
	public static final int ITEM_IMAGE = 2;
	public static final int ITEM_FILE = 3;

	private final File file;
	private final int item_type;
	private final int sort_order; // items are sorted first by sort_order, then alphabetically
	private boolean selected;

	FileWrapper(File file, int item_type) {
		this.file = file;
		this.item_type = item_type;
		switch (item_type) {
			case ITEM_LEVEL_UP:
				sort_order = 0;
				break;
			case ITEM_FOLDER:
				sort_order = 1;
				break;
			default:
				sort_order = 2;
		}
	}

	@Override
	public String toString() {
		if (item_type == ITEM_LEVEL_UP)
			return "";
		return file.getName();
	}

	@Override
	public int compareTo(@NonNull FileWrapper o) {
		if( this.sort_order < o.sort_order )
			return -1;
		else if( this.sort_order > o.sort_order )
			return 1;
		return this.file.getName().toLowerCase(Locale.US).compareTo(o.getFile().getName().toLowerCase(Locale.US));
	}

	@Override
	public boolean equals(Object o) {
		// important to override equals(), since we're overriding compareTo()
		if( !(o instanceof FileWrapper) )
			return false;
		FileWrapper that = (FileWrapper)o;
		if( this.sort_order != that.sort_order )
			return false;
		return this.file.getName().toLowerCase(Locale.US).equals(that.getFile().getName().toLowerCase(Locale.US));
	}

	@Override
	public int hashCode() {
		// must override this, as we override equals()
		return this.file.getName().toLowerCase(Locale.US).hashCode();
	}

	public File getFile() {
		return file;
	}

	public int getItemType() {
		return item_type;
	}
	
	public void reverseSelected() {
		selected = !selected;
	}
	
	public boolean getSelected() {
		return selected;
	}
}

class FileListAdapter extends ArrayAdapter<FileWrapper> {
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
	
	public List<FileWrapper> getList() {
		return this.list;
	}
}

