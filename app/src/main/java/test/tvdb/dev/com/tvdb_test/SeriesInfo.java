package test.tvdb.dev.com.tvdb_test;

/**
 * Created by daniele on 07/03/2015.
 */

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.echo.holographlibrary.PieGraph;
import com.echo.holographlibrary.PieSlice;
import com.melnykov.fab.FloatingActionButton;
import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.TvDbException;
import com.omertron.thetvdbapi.model.Episode;
import com.omertron.thetvdbapi.model.Series;
import com.uwetrottmann.trakt.v2.TraktV2;
import com.uwetrottmann.trakt.v2.enums.Extended;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

public class SeriesInfo extends ActionBarActivity
{
    private SlidingTabLayout tabLayout;
    private ViewPager viewPager;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tabs);
        toolbar=(Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        String title=getIntent().getExtras().getString("TITLE");
        getSupportActionBar().setTitle(title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tabLayout=(SlidingTabLayout)findViewById(R.id.tabs);
        viewPager=(ViewPager)findViewById(R.id.pager);
        viewPager.setAdapter(new MyFragmentAdapter(getSupportFragmentManager(),getIntent()));
        tabLayout.setViewPager(viewPager);
        tabLayout.setDistributeEvenly(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    class MyFragmentAdapter extends FragmentPagerAdapter
    {
        private String[] tabs;
        private Intent intent;
        public MyFragmentAdapter(FragmentManager fm,Intent intent) {
            super(fm);
            tabs=getResources().getStringArray(R.array.tabs);
            this.intent=intent;
        }

        @Override
        public Fragment getItem(int position) {
            MyFragment fragment=MyFragment.getIstance(position,intent,toolbar);
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabs[position];
        }

        @Override
        public int getCount() {
            return 4;
        }
    }

    public static class MyFragment extends Fragment
    {
        private ListView actorList;
        private ExpandableListView episodesList;
        private TheTVDBApi tvDB;
        private View rootView;
        private static Toolbar toolbar;
        private static Intent intent;

        public static MyFragment getIstance(int position,Intent _intent,Toolbar _toolbar)
        {
            MyFragment fragment=new MyFragment();
            intent=_intent;
            toolbar=_toolbar;
            Bundle extras=intent.getExtras();
            extras.putInt("position",position);
            fragment.setArguments(extras);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            final Bundle extras = getArguments();
            switch (extras.getInt("position")) {
                case 0:
                    rootView = inflater.inflate(R.layout.fragment_episodes, container, false);
                    final ArrayList<String> updatedWatches=new ArrayList<>();
                    episodesList = (ExpandableListView) rootView.findViewById(R.id.listView);
                    final ArrayList<Season> tmpSeasons =(ArrayList<Season>)extras.getSerializable("EPISODES");
                    if(tmpSeasons.get(0).getSeasonNumber()==0)
                    {
                        Season tmp=tmpSeasons.remove(0);
                        tmpSeasons.add(tmp);
                    }
                    ArrayList<String> tmp=new ArrayList<>();
                    ArrayList<ArrayList<String>> tmpIDs=new ArrayList<>();
                    Database db=new Database(getActivity());
                    SQLiteDatabase sqlDb=db.getReadableDatabase();
                    final ArrayList<ArrayList<Boolean>> watches=new ArrayList<>();
                    for(int j=0;j<tmpSeasons.size();j++) {
                        watches.add(new ArrayList<Boolean>());
                        for (int i = 1; i <= tmpSeasons.get(j).getTotEpisodes(); i++) {
                            Cursor cursor = sqlDb.rawQuery("SELECT SEEN FROM EPISODES WHERE ID_EPISODES=" + tmpSeasons.get(j).getEpisode(i).getId(), null);
                            while (cursor.moveToNext())
                                watches.get(j).add(cursor.getInt(cursor.getColumnIndex("SEEN")) == 0 ? false : true);
                        }
                    }
                    FloatingActionButton fab = (FloatingActionButton)rootView.findViewById(R.id.fab);
                    fab.attachToListView(episodesList);
                    fab.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new UpdatedWatches().execute(updatedWatches);
                            ShowDate showDate=new ShowDate(watches);
                            MetaEpisode metaData=new MetaEpisode();
                            metaData.id=extras.getString("ID");
                            showDate.fillMetaData(metaData, tmpSeasons);
                            new FetchDate().execute(metaData);
                        }
                    });
                    for(int j=0;j<tmpSeasons.size();j++) {
                        tmpIDs.add(new ArrayList<String>());
                        for (int i = 1; i <=tmpSeasons.get(j).getTotEpisodes(); i++) {
                            tmp.add(tmpSeasons.get(j).getEpisode(i).getEpisodeName());
                            tmpIDs.get(j).add(tmpSeasons.get(j).getEpisode(i).getId());
                        }
                    }
                    ShowDate showDate=new ShowDate(watches);
                    MetaEpisode metaData=new MetaEpisode();
                    metaData.id=extras.getString("ID");
                    showDate.fillMetaData(metaData, tmpSeasons);
                    new FetchDate().execute(metaData);
                    EpisodesAdapter adapter = new EpisodesAdapter(getActivity(), Arrays.copyOf(tmp.toArray(), tmp.size(), String[].class),
                            intent.getExtras().getString("ID"),updatedWatches,watches,tmpIDs,tmpSeasons);
                    episodesList.setAdapter(adapter);
                    return rootView;
                case 1:
                    rootView = inflater.inflate(R.layout.fragment_rating, container, false);
                    new GetRating().execute();
                    return rootView;
                case 2:
                    rootView = inflater.inflate(R.layout.fragment_actors, container, false);
                    actorList = (ListView) rootView.findViewById(R.id.actors_list);
                    ArrayList<String> _tmp = extras.getStringArrayList("ACTORS");
                    ArrayAdapter<String> _adapter=new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_1,
                                                                           Arrays.copyOf(_tmp.toArray(), _tmp.size(), String[].class));
                    actorList.setAdapter(_adapter);
                    return rootView;
                case 3:
                    rootView = inflater.inflate(R.layout.fragment_stats, container, false);
                    final ArrayList<Season> _tmpSeasons =(ArrayList<Season>)extras.getSerializable("EPISODES");
                    if(_tmpSeasons.get(0).getSeasonNumber()==0)
                    {
                        Season tmp1=_tmpSeasons.remove(0);
                        _tmpSeasons.add(tmp1);
                    }
                    ArrayList<String> tmp1=new ArrayList<>();
                    ArrayList<ArrayList<String>> _tmpIDs=new ArrayList<>();
                    Database _db=new Database(getActivity());
                    SQLiteDatabase _sqlDb=_db.getReadableDatabase();
                    final ArrayList<ArrayList<Boolean>> _watches=new ArrayList<>();
                    for(int j=0;j<_tmpSeasons.size();j++) {
                        _watches.add(new ArrayList<Boolean>());
                        for (int i = 1; i <= _tmpSeasons.get(j).getTotEpisodes(); i++) {
                            Cursor cursor = _sqlDb.rawQuery("SELECT SEEN FROM EPISODES WHERE ID_EPISODES=" + _tmpSeasons.get(j).getEpisode(i).getId(), null);
                            while (cursor.moveToNext())
                                _watches.get(j).add(cursor.getInt(cursor.getColumnIndex("SEEN")) == 0 ? false : true);
                        }
                    }
                    int countAsSeenSeasons=0,countAsSeenEpisodes=0,totalEpisodes=0;
                    for(int i=0;i<_watches.size();i++) {
                        boolean seen=false;
                        for (int j = 0; j < _watches.get(i).size(); j++)
                            if (!_watches.get(i).get(j))
                                break;
                            else if(j==_watches.get(i).size()-1&&_watches.get(i).get(j))
                                seen=true;
                        if(seen)
                        {
                            countAsSeenSeasons++;
                            seen=false;
                        }
                    }
                    for(int i=0;i<_watches.size();i++)
                        for(int j=0;j<_watches.get(i).size();j++) {
                            totalEpisodes++;
                            if (_watches.get(i).get(j))
                                countAsSeenEpisodes++;
                        }
                    ((TextView)rootView.findViewById(R.id.episodes)).setText("Episodes seen :");
                    PieGraph pg = (PieGraph)rootView.findViewById(R.id.graph_episodes);
                    pg.setThickness(20);
                    PieSlice slice=new PieSlice();
                    slice.setColor(Color.parseColor("#4CAF50"));
                    slice.setValue(countAsSeenEpisodes);
                    pg.addSlice(slice);
                    PieSlice _slice=new PieSlice();
                    _slice.setColor(Color.parseColor("#858585"));
                    _slice.setValue(totalEpisodes - countAsSeenEpisodes);
                    pg.addSlice(_slice);
                    ((TextView)rootView.findViewById(R.id.textView_episode)).setText(countAsSeenEpisodes + "/" + totalEpisodes);
                    ((TextView)rootView.findViewById(R.id.seasons)).setText("Seasons seen :");
                    PieGraph pgS = (PieGraph)rootView.findViewById(R.id.graph_season);
                    pgS.setThickness(20);
                    PieSlice sliceS=new PieSlice();
                    sliceS.setColor(Color.parseColor("#4CAF50"));
                    sliceS.setValue(countAsSeenSeasons);
                    pgS.addSlice(sliceS);
                    PieSlice _sliceS=new PieSlice();
                    _sliceS.setColor(Color.parseColor("#858585"));
                    _sliceS.setValue(_watches.size()-countAsSeenSeasons);
                    pgS.addSlice(_sliceS);
                    ((TextView)rootView.findViewById(R.id.textView_season)).setText(countAsSeenSeasons + "/" + _watches.size());
                    return rootView;

                default : return rootView;
            }
        }

        private class FetchDate extends AsyncTask<MetaEpisode,Void,Void>
        {
            private Episode episode;
            private String outputDate;

            @Override
            protected Void doInBackground(MetaEpisode... params) {
                try
                {
                    if(tvDB==null)
                        tvDB=new TheTVDBApi("2C8BD989F33B0C84");
                    if(!params[0].full) {
                        if (params[0].season == 0)
                            params[0].season += params[0].seasonOffset;
                        String date;
                        do
                        {
                            tvDB=new TheTVDBApi("2C8BD989F33B0C84");
                            try
                            {
                                episode = tvDB.getEpisode(params[0].id, params[0].season, params[0].index, "en");
                            }
                            catch(TvDbException exc)
                            {
                                episode = tvDB.getEpisode(params[0].id, params[0].season+1, params[0].index, "en");
                            }
                            date=episode.getFirstAired();
                            if(date.equals(""))
                                params[0].index++;
                        }while(date.equals(""));
                        String[] splitDate = date.split("-");
                        Date currentDate = new Date();
                        Calendar episodeDate = Calendar.getInstance();
                        episodeDate.set(Integer.parseInt(splitDate[0]), Integer.parseInt(splitDate[1]) - 1, Integer.parseInt(splitDate[2]));
                        if (currentDate.compareTo(episodeDate.getTime()) < 0)
                            outputDate = "Next episode air on ";
                        else
                            outputDate = "Next episode aired on ";
                        outputDate += splitDate[2] + "/" + splitDate[1] + "/" + splitDate[0];
                    }
                    else
                        outputDate="Concluded";
                }
                catch(TvDbException exc)
                {
                    exc.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                    toolbar.setSubtitle(outputDate);
            }
        }

        private class GetRating extends AsyncTask<Void,Void,Void>
        {
            private String rating;

            @Override
            protected Void doInBackground(Void... params) {
                try
                {
                    if(tvDB==null)
                        tvDB=new TheTVDBApi("2C8BD989F33B0C84");
                    Series series=tvDB.getSeries(intent.getExtras().getString("ID"),"en");
                    rating=series.getRating();
                }
                catch(TvDbException exc)
                {

                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                PieGraph pg = (PieGraph)rootView.findViewById(R.id.graph);
                pg.setThickness(20);
                PieSlice slice=new PieSlice();
                slice.setColor(Color.parseColor("#4CAF50"));
                float tmp_rating;
                slice.setValue(tmp_rating=Float.parseFloat(rating));
                pg.addSlice(slice);
                PieSlice _slice=new PieSlice();
                _slice.setColor(Color.parseColor("#858585"));
                _slice.setValue(11-tmp_rating);
                pg.addSlice(_slice);
                ((TextView) rootView.findViewById(R.id.textView)).setText(rating+"/10");
            }
        }

        private class UpdatedWatches extends AsyncTask<ArrayList<String>,Void,Void> {
            @Override
            protected Void doInBackground(ArrayList<String>... params) {
                Database db = new Database(getActivity());
                SQLiteDatabase sqlDb = db.getWritableDatabase();
                ContentValues contentValues = new ContentValues();
                for (int i = 0; i < params[0].size(); i++) {
                    String data = params[0].get(i);
                    String[] meta = data.split(" ");
                    contentValues.put("SEEN", Integer.parseInt(meta[1]));
                    sqlDb.update("EPISODES", contentValues, "ID_EPISODES=" + meta[0], null);
                }
                sqlDb.close();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Toast.makeText(getActivity(), "Watches updated", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

