package org.illusion.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceDrawerActivity;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Switch;
import android.widget.TextView;

import org.illusion.settings.R;

public class SettingsActivity extends PreferenceDrawerActivity implements ButtonBarHandler {

    private static final String TAG = "Illusion_Settings";

    private static boolean hasNotificationLed;
    private static boolean hasSPen;
//    private static String KEY_USE_ENGLISH_LOCALE = "use_english_locale";

    protected HashMap<Integer, Integer> mHeaderIndexMap = new HashMap<Integer, Integer>();
    private List<Header> mHeaders;

    private String mFragmentClass;
    private int mTopLevelHeaderId;
    private Header mFirstHeader;
    private Header mCurrentHeader;
    boolean mInLocalHeaderSwitch;

//    Locale defaultLocale;

    protected boolean isShortcut;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mInLocalHeaderSwitch = true;
        super.onCreate(savedInstanceState);
        mInLocalHeaderSwitch = false;

        if (!onIsHidingHeaders() && onIsMultiPane()) {
            highlightHeader();
            // Force the title so that it doesn't get overridden by a direct
            // launch of
            // a specific settings screen.
            setTitle(R.string.app_name);
        }

        ActionBar actionBar = getActionBar();
        Random generator = new Random();
        int backgroundNumber = generator.nextInt(5);
        BitmapDrawable background;
        switch (backgroundNumber) {
            case 0:
                background = new BitmapDrawable(BitmapFactory
                        .decodeResource(getResources(), R.drawable.ab_background));
                break;
            case 1:
                background = new BitmapDrawable(BitmapFactory
                        .decodeResource(getResources(), R.drawable.ab_background1));
                break;
            case 2:
                background = new BitmapDrawable(BitmapFactory
                        .decodeResource(getResources(), R.drawable.ab_background2));
                break;
            case 3:
                background = new BitmapDrawable(BitmapFactory
                        .decodeResource(getResources(), R.drawable.ab_background3));
                break;
            case 4:
                background = new BitmapDrawable(BitmapFactory
                        .decodeResource(getResources(), R.drawable.ab_background4));
                break;
            default:
                background = new BitmapDrawable(BitmapFactory
                        .decodeResource(getResources(), R.drawable.ab_background));
        }
        background.setTileModeX(Shader.TileMode.CLAMP);
        actionBar.setBackgroundDrawable(background);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);

    }

    @Override
    public void onBackPressed() {
        if (isShortcut) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
        ArrayList<Header> toRemove = new ArrayList<Header>();
        for (int i=0; i<target.size(); i++) {
            Header header = target.get(i);
        }
        for (int i=0; i<toRemove.size(); i++) {
            target.remove(toRemove.get(i));
        }
        updateHeaderList(target);
        mHeaders = target;
    }

    /**
     * Override initial header when an activity-alias is causing Settings to be
     * launched for a specific fragment encoded in the android:name parameter.
     */
    @Override
    public Header onGetInitialHeader() {
        String fragmentClass = getStartingFragmentClass(super.getIntent());
        if (fragmentClass != null) {
            Header header = new Header();
            header.fragment = fragmentClass;
            header.title = getTitle();
            header.fragmentArguments = getIntent().getExtras();
            mCurrentHeader = header;
            return header;
        }

        return mFirstHeader;
    }

    @Override
    public boolean hasNextButton() {
        return super.hasNextButton();
    }

    @Override
    public Button getNextButton() {
        return super.getNextButton();
    }

    private void highlightHeader() {
        if (mTopLevelHeaderId != 0) {
            Integer index = mHeaderIndexMap.get(mTopLevelHeaderId);
            if (index != null) {
                getListView().setItemChecked(index, true);
                getListView().smoothScrollToPosition(index);
            }
        }
    }

    private void updateHeaderList(List<Header> target) {
        int i = 0;
        while (i < target.size()) {
            Header header = target.get(i);
            // Ids are integers, so downcasting
            int id = (int) header.id;

            // Increment if the current one wasn't removed by the Utils code.
            if (target.get(i) == header) {
                // Hold on to the first header, when we need to reset to the
                // top-level
                if (mFirstHeader == null &&
                        HeaderAdapter.getHeaderType(header) != HeaderAdapter.HEADER_TYPE_CATEGORY) {
                    mFirstHeader = header;
                }
                mHeaderIndexMap.put(id, i);
                i++;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

//        setLocale();

        ListAdapter listAdapter = getListAdapter();
        if (listAdapter instanceof HeaderAdapter) {
            ((HeaderAdapter) listAdapter).resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        ListAdapter listAdapter = getListAdapter();
        if (listAdapter instanceof HeaderAdapter) {
            ((HeaderAdapter) listAdapter).pause();
        }
    }

    protected String getStartingFragmentClass(Intent intent) {
        if (mFragmentClass != null)
            return mFragmentClass;

        String intentClass = intent.getComponent().getClassName();
        if (intentClass.equals(getClass().getName()))
            return null;

        return intentClass;
    }

    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (mHeaders == null) {
            mHeaders = new ArrayList<Header>();
            // When the saved state provides the list of headers, onBuildHeaders
            // is not called
            // Copy the list of Headers from the adapter, preserving their order
            for (int i = 0; i < adapter.getCount(); i++) {
                mHeaders.add((Header) adapter.getItem(i));
            }
        }

        // Ignore the adapter provided by PreferenceActivity and substitute ours
        // instead
        super.setListAdapter(new HeaderAdapter(this, mHeaders));
    }

    private static class HeaderAdapter extends ArrayAdapter<Header> {
        static final int HEADER_TYPE_CATEGORY = 0;
        static final int HEADER_TYPE_NORMAL = 1;
        static final int HEADER_TYPE_SWITCH = 2;
        private static final int HEADER_TYPE_COUNT = HEADER_TYPE_SWITCH + 1;

        private static class HeaderViewHolder {
            ImageView icon;
            TextView title;
            TextView summary;
            Switch switch_;
        }

        private LayoutInflater mInflater;

        static int getHeaderType(Header header) {
            if (header.fragment == null && header.intent == null) {
                return HEADER_TYPE_CATEGORY;
            } else {
                return HEADER_TYPE_NORMAL;
            }
        }

        @Override
        public int getItemViewType(int position) {
            Header header = getItem(position);
            return getHeaderType(header);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false; // because of categories
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) != HEADER_TYPE_CATEGORY;
        }

        @Override
        public int getViewTypeCount() {
            return HEADER_TYPE_COUNT;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        public HeaderAdapter(Context context, List<Header> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            // Temp Switches provided as placeholder until the adapter replaces
            // these with actual
            // Switches inflated from their layouts. Must be done before adapter
            // is set in super
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HeaderViewHolder holder;
            Header header = getItem(position);
            int headerType = getHeaderType(header);
            View view = null;

            if (convertView == null) {
                holder = new HeaderViewHolder();
                switch (headerType) {
                    case HEADER_TYPE_CATEGORY:
                        view = new TextView(getContext(), null,
                                android.R.attr.listSeparatorTextViewStyle);
                        holder.title = (TextView) view;
                        break;

                    case HEADER_TYPE_NORMAL:
                        view = mInflater.inflate(
                                com.android.internal.R.layout.preference_header_item, parent,
                                false);
                        holder.icon = (ImageView) view.findViewById(com.android.internal.R.id.icon);
                        holder.title = (TextView)
                                view.findViewById(com.android.internal.R.id.title);
                        holder.summary = (TextView)
                                view.findViewById(com.android.internal.R.id.summary);
                        break;
                }
                view.setTag(holder);
            } else {
                view = convertView;
                holder = (HeaderViewHolder) view.getTag();
            }

            // All view fields must be updated every time, because the view may
            // be recycled
            switch (headerType) {
                case HEADER_TYPE_CATEGORY:
                    holder.title.setText(header.getTitle(getContext().getResources()));
                    break;
                case HEADER_TYPE_NORMAL:
                    holder.icon.setImageResource(header.iconRes);
                    holder.title.setText(header.getTitle(getContext().getResources()));
                    CharSequence summary = header.getSummary(getContext().getResources());
                    if (!TextUtils.isEmpty(summary)) {
                        holder.summary.setVisibility(View.VISIBLE);
                        holder.summary.setText(summary);
                    } else {
                        holder.summary.setVisibility(View.GONE);
                    }
                    break;
            }

            return view;
        }

        public void resume() {
        }

        public void pause() {
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        // Override the fragment title for Wallpaper settings
        int titleRes = pref.getTitleRes();

        startPreferencePanel(pref.getFragment(), pref.getExtras(), titleRes, null, null, 0);
        return true;
    }

}
