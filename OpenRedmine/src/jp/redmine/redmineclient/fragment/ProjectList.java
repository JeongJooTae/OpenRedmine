package jp.redmine.redmineclient.fragment;

import java.sql.SQLException;

import com.j256.ormlite.android.apptools.OrmLiteListFragment;

import jp.redmine.redmineclient.R;
import jp.redmine.redmineclient.adapter.RedmineProjectListAdapter;
import jp.redmine.redmineclient.db.cache.DatabaseCacheHelper;
import jp.redmine.redmineclient.db.cache.RedmineUserModel;
import jp.redmine.redmineclient.entity.RedmineConnection;
import jp.redmine.redmineclient.entity.RedmineProject;
import jp.redmine.redmineclient.entity.RedmineUser;
import jp.redmine.redmineclient.form.StatusUserForm;
import jp.redmine.redmineclient.fragment.IssueView.OnArticleSelectedListener;
import jp.redmine.redmineclient.model.ConnectionModel;
import jp.redmine.redmineclient.param.ConnectionArgument;
import jp.redmine.redmineclient.task.SelectProjectTask;
import android.app.Activity;
import android.os.Bundle;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class ProjectList extends OrmLiteListFragment<DatabaseCacheHelper> {
	private static final String TAG = ProjectList.class.getSimpleName();
	private RedmineProjectListAdapter adapter;
	private SelectDataTask task;
	private MenuItem menu_refresh;
	private View mHeader;
	private View mFooter;
	private OnArticleSelectedListener mListener;
	private ConnectionList.OnArticleSelectedListener mConnectionListener;

	public ProjectList(){
		super();
	}

	static public ProjectList newInstance(ConnectionArgument intent){
		ProjectList instance = new ProjectList();
		instance.setArguments(intent.getArgument());
		return instance;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if(activity instanceof ActivityInterface){
			ActivityInterface aif = (ActivityInterface)activity;
			mListener = aif.getHandler(OnArticleSelectedListener.class);
			mConnectionListener = aif.getHandler(ConnectionList.OnArticleSelectedListener.class);
		}
		if(mListener == null) {
			//setup empty events
			mListener = new OnArticleSelectedListener() {
				@Override
				public void onIssueFilterList(int connectionId, int filterid) {}
				@Override
				public void onIssueList(int connectionId, long projectId) {}
				@Override
				public void onIssueSelected(int connectionid, int issueid) {}
				@Override
				public void onIssueEdit(int connectionid, int issueid) {}
				@Override
				public void onIssueRefreshed(int connectionid, int issueid) {}
				@Override
				public void onIssueAdd(int connectionId, long projectId) {}
			};
		}
		if(mConnectionListener == null) {
			//setup empty events
			mConnectionListener = new ConnectionList.OnArticleSelectedListener() {
				@Override
				public void onConnectionSelected(int connectionid) {}
				@Override
				public void onConnectionEdit(int connectionid) {}
				@Override
				public void onConnectionAdd() {}
			};
		}

	}

	@Override
	public void onDestroyView() {
		cancelTask();
		setListAdapter(null);
		super.onDestroyView();
	}
	protected void cancelTask(){
		// cleanup task
		if(task != null && task.getStatus() == Status.RUNNING){
			task.cancel(true);
		}
	}
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getListView().addFooterView(mFooter);
		getListView().setFastScrollEnabled(true);

		adapter = new RedmineProjectListAdapter(getHelper());

		ConnectionArgument intent = new ConnectionArgument();
		intent.setArgument(getArguments());
		adapter.setupParameter(intent.getConnectionId());
		adapter.notifyDataSetInvalidated();
		adapter.notifyDataSetChanged();

		RedmineUserModel mUserModel = new RedmineUserModel(getHelper());
		RedmineUser user = null;
		try {
			user = mUserModel.fetchCurrentUser(intent.getConnectionId());
		} catch (SQLException e) {
			Log.e(TAG,"fetchCurrentUser", e);
		}
		if(user != null){
			StatusUserForm formHeader = new StatusUserForm(mHeader);
			formHeader.setValue(user);
			getListView().addHeaderView(mHeader);

		}

		setListAdapter(adapter);
		if(adapter.getCount() < 1){
			onRefresh();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mFooter = inflater.inflate(R.layout.listview_footer,null);
		mFooter.setVisibility(View.GONE);
		mHeader = inflater.inflate(R.layout.status_user,null);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public void onListItemClick(ListView parent, View v, int position, long id) {
		super.onListItemClick(parent, v, position, id);
		ListView listView = (ListView) parent;
		Object item =  listView.getItemAtPosition(position);
		if(item == null || !(item instanceof RedmineProject))
			return;
		RedmineProject project = (RedmineProject)item;
		mListener.onIssueList(project.getConnectionId(), project.getId());
	}

	protected void onRefresh(){
		if(task != null && task.getStatus() == Status.RUNNING){
			return;
		}
		ConnectionArgument intent = new ConnectionArgument();
		intent.setArgument(getArguments());
		int id = intent.getConnectionId();
		ConnectionModel mConnection = new ConnectionModel(getActivity());
		RedmineConnection connection = mConnection.getItem(id);
			mConnection.finalize();
		task = new SelectDataTask(getHelper());
		task.execute(connection);
	}

	private class SelectDataTask extends SelectProjectTask {
		public SelectDataTask(DatabaseCacheHelper helper) {
			super(helper);
		}

		// can use UI thread here
		@Override
		protected void onPreExecute() {
			mFooter.setVisibility(View.VISIBLE);
			if(menu_refresh != null)
				menu_refresh.setEnabled(false);
		}

		// can use UI thread here
		@Override
		protected void onPostExecute(Void b) {
			mFooter.setVisibility(View.GONE);
			adapter.notifyDataSetInvalidated();
			adapter.notifyDataSetChanged();
			if(menu_refresh != null)
				menu_refresh.setEnabled(true);
		}

		@Override
		protected void onProgress(int max, int proc) {
			adapter.notifyDataSetInvalidated();
			adapter.notifyDataSetChanged();
			super.onProgress(max, proc);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate( R.menu.projects, menu );
		menu_refresh = menu.findItem(R.id.menu_refresh);
		if(task != null && task.getStatus() == Status.RUNNING)
			menu_refresh.setEnabled(false);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch ( item.getItemId() )
		{
			case R.id.menu_refresh:
			{
				this.onRefresh();
				return true;
			}
			case R.id.menu_settings:
			{
				ConnectionArgument input = new ConnectionArgument();
				input.setArgument(getArguments());
				mConnectionListener.onConnectionEdit(input.getConnectionId());
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

}
