package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;

//
// There is an unfortunate amount of code in this class that is very 
// similar to the code in the FindStopActivity. However, the code is different
// in enough ways that it's a bit difficult to determine whether or not 
// refactoring to share the code would make sense.
//
public class FindRouteActivity extends ListActivity {
    private static final String TAG = "FindRouteActivity";
    
    private RoutesDbAdapter mDbAdapter;
    private boolean mShortcutMode = false;
    
    private FindRouteTask mAsyncTask;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        setContentView(R.layout.find_route);
        registerForContextMenu(getListView());
        
        Intent myIntent = getIntent();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
            mShortcutMode = true;
        }
        
        setTitle(R.string.find_route_title);
        
        mDbAdapter = new RoutesDbAdapter(this);
        mDbAdapter.open();
        
        TextView textView = (TextView)findViewById(R.id.search_text);
        textView.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {    
                if (s.length() >= 1) {
                    doSearch(s);
                }
                else if (s.length() == 0) {
                    fillFavorites();
                }
            }
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {            
            }
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {                
            }
        });

        fillFavorites();
    }
    @Override
    protected void onDestroy() {
        mDbAdapter.close();
        super.onDestroy();
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String routeId;
        String routeName;
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            routeId = c.getString(RoutesDbAdapter.ROUTE_COL_ROUTEID);
            routeName = c.getString(RoutesDbAdapter.ROUTE_COL_SHORTNAME);
        }
        else if (adapter instanceof SearchResultsListAdapter) {
            ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
            routeId = route.getId();
            routeName = route.getShortName();
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return;
        }
        
        if (mShortcutMode) {
            makeShortcut(routeId, routeName);
        }
        else {
            RouteInfoActivity.start(this, routeId);        
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.find_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_favorites) {
            RoutesDbAdapter.clearFavorites(this);
            ListAdapter adapter = getListView().getAdapter();
            if (adapter instanceof SimpleCursorAdapter) {
                ((SimpleCursorAdapter)adapter).getCursor().requery();
            }
            return true;
        }
        return false;
    }
    public final void onSearch(View v) {
        TextView textView = (TextView)findViewById(R.id.search_text);
        doSearch(textView.getText());             
    }
    
    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.short_name);
        menu.setHeaderTitle(getString(R.string.route_name, text.getText()));
        if (mShortcutMode) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_create_shortcut);
        }
        else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_get_route_info);            
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.find_context_showonmap);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DEFAULT:
            // Fake a click
            onListItemClick(getListView(), info.targetView, info.position, info.id);
            return true;
        case CONTEXT_MENU_SHOW_ON_MAP:
            showOnMap(getListView(), info.position);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    private void showOnMap(ListView l, int position) {
        String routeId;
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            routeId = c.getString(RoutesDbAdapter.ROUTE_COL_ROUTEID);
        }
        else if (adapter instanceof SearchResultsListAdapter) {
            ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
            routeId = route.getId();
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return;
        }
        Intent myIntent = new Intent(this, MapViewActivity.class);
        myIntent.putExtra(MapViewActivity.ROUTE_ID, routeId);
        startActivity(myIntent);
    }
    
    private void fillFavorites() {
        // Cancel any current search.
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
            mAsyncTask = null;
        }
        Cursor c = mDbAdapter.getFavoriteRoutes();
        startManagingCursor(c);
        
        // Make sure the "empty" text is correct.
        TextView empty = (TextView) findViewById(android.R.id.empty);
        empty.setText(R.string.find_hint_nofavoriteroutes);
        
        final String[] from = { 
                DbHelper.KEY_SHORTNAME,
                DbHelper.KEY_LONGNAME 
        };
        final int[] to = {
                R.id.short_name,
                R.id.long_name
        };
        SimpleCursorAdapter simpleAdapter = 
            new SimpleCursorAdapter(this, R.layout.find_route_listitem, c, from, to);
        setListAdapter(simpleAdapter);
    }
    
    private void makeShortcut(String routeId, String routeName) {
        // Set up the container intent
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, 
            RouteInfoActivity.makeIntent(this, routeId));
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, routeName);
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(
                this,  R.drawable.icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher
        setResult(RESULT_OK, intent);
        finish();        
    }
    
    private final class SearchResultsListAdapter extends Adapters.BaseRouteArrayAdapter {
        public SearchResultsListAdapter(ObaResponse response) {
            super(FindRouteActivity.this, 
                    response.getData().getRoutes(), 
                    R.layout.find_route_listitem);
        }
        @Override
        protected void setData(View view, int position) {
            TextView shortName = (TextView)view.findViewById(R.id.short_name);
            TextView longName = (TextView)view.findViewById(R.id.long_name);

            ObaRoute route = mArray.getRoute(position);
            shortName.setText(route.getShortName());
            longName.setText(route.getLongName());
        }
    }

    private final AsyncTasks.Progress mTitleProgress 
        = new AsyncTasks.ProgressIndeterminateVisibility(this);

    private class FindRouteTask extends AsyncTasks.StringToResponse {
        public FindRouteTask() {
            super(mTitleProgress);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            String routeId = params[0];
            return ObaApi.getRoutesByLocation(
                    FindStopActivity.getLocation(FindRouteActivity.this), 0, routeId);
        }
        @Override
        protected void doResult(ObaResponse result) {
            TextView empty = (TextView) findViewById(android.R.id.empty);
            if (result.getCode() == ObaApi.OBA_OK) {
                empty.setText(R.string.find_hint_noresults);
                setListAdapter(new SearchResultsListAdapter(result));
            }
            else {
                empty.setText(R.string.generic_comm_error);
            }
        }
    }
    
    
    private void doSearch(CharSequence text) {
        if (text.length() == 0) {
            return;
        }
        if (mAsyncTask != null) {
            // Try to cancel it
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new FindRouteTask();
        mAsyncTask.execute(text.toString());        
    }
}
