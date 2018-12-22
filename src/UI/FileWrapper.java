package com.caddish_hedgehog.hedgecam2.UI;

import android.support.annotation.NonNull;

import java.io.File;
import java.util.Locale;

public class FileWrapper implements Comparable<FileWrapper> {
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
