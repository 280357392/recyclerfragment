package fr.nihilus.recyclerfragment;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import static android.support.v7.widget.RecyclerView.Adapter;
import static android.support.v7.widget.RecyclerView.ViewHolder;

/**
 * <p>A fragment that hosts a RecyclerView to display a set of items.</p>
 * <p>RecyclerFragment has a default layout that consists of a single RecyclerView.
 * Howether if yo desire, you can customize the fragment layout by returning your own view hierarchy from
 * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
 * To do this, your view hierarchy <em>must</em> contain the following views :
 * <ul>
 * <li>a RecyclerView with id "@id/recycler"</li>
 * <li>any View with id "@id/progress"</li>
 * <li>any ViewGroup with id "@id/recycler_container"</li>
 * </ul>
 * <p>Optionally, your view hierarchy can contain another view object of any type to display
 * when the recycler view is empty.
 * This empty view must have an id "@id/empty". Note that when an empty view is present,
 * the recycler view will be hidden when there is no data to display.
 */
@SuppressWarnings("unused")
public class RecyclerFragment extends Fragment {
    private static final String TAG = "RecyclerFragment";
    private static final int MIN_DELAY = 500;
    private static final int MIN_SHOW_TIME = 500;

    private Adapter<? extends RecyclerView.ViewHolder> mAdapter;
    private RecyclerView mRecycler;
    private View mProgress;
    private View mRecyclerContainer;
    private View mEmptyView;

    private final Handler mHandler = new Handler();

    private long mStartTime = -1;
    private boolean mPostedHide = false;
    private boolean mPostedShow = false;
    private boolean mDismissed = false;
    private boolean mObserverRegistered = false;

    private final AdapterDataObserver mEmptyStateObserver = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            if (mRecycler != null) {
                // Show / hide the empty view only when fragment is visible
                setEmptyShown(mAdapter != null && mAdapter.getItemCount() == 0);
            }
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            onChanged();
        }
    };

    private final Runnable mDelayedShow = new Runnable() {
        @Override
        public void run() {
            mPostedShow = false;
            mStartTime = -1;
            setRecyclerShownImmediate(true);
        }
    };

    private final Runnable mDelayedHide = new Runnable() {
        @Override
        public void run() {
            mPostedHide = false;
            if (!mDismissed) {
                mStartTime = System.currentTimeMillis();
                setRecyclerShownImmediate(false);
            }
        }
    };

    public RecyclerFragment() {
        // Required empty constructor
    }

    /**
     * <p>Called to have this RecyclerFragment instanciate its view hierarchy.</p>
     * <p>The default implementation creates a layout containing a RecyclerView and a ProgressBar.
     * You can override this method to define your own view hierarchy for this fragment.
     * If you include a view with id "@id/empty", its visibility will automatically change depending
     * on the empty state of the adapter.</p>
     *
     * @return the view for this fragment UI
     */
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recycler, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureRecycler();
    }

    @Override
    public void onDestroyView() {
        unregisterEmptyStateObserver(mAdapter);
        mHandler.removeCallbacks(mDelayedShow);
        mHandler.removeCallbacks(mDelayedHide);
        mPostedHide = mPostedShow = false;
        mRecycler = null;
        mRecyclerContainer = mEmptyView = mProgress = null;

        super.onDestroyView();
    }

    /**
     * Sets the RecyclerView.LayoutManager object for the RecyclerView hosted by this fragment.
     * If you don't specify a layout manager, the default implementation will
     * use a vertical LinearLayoutManager.
     *
     * @param manager the layout manager used to lay out items in this fragment's recycler view
     */
    public void setLayoutManager(@Nullable RecyclerView.LayoutManager manager) {
        ensureRecycler();
        mRecycler.setLayoutManager(manager);
    }

    private void setEmptyShown(boolean shown) {
        // Show/hide empty view and recycler only if empty view is defined
        if (mEmptyView != null) {
            mRecycler.setVisibility(shown ? View.GONE : View.VISIBLE);
            mEmptyView.setVisibility(shown ? View.VISIBLE : View.GONE);
        }
    }

    private void registerEmptyStateObserver(Adapter<? extends ViewHolder> adapter) {
        if (adapter != null && !mObserverRegistered) {
            adapter.registerAdapterDataObserver(mEmptyStateObserver);
            mObserverRegistered = true;
        }
    }

    private void unregisterEmptyStateObserver(Adapter<? extends ViewHolder> adapter) {
        if (adapter != null && mObserverRegistered) {
            adapter.unregisterAdapterDataObserver(mEmptyStateObserver);
            mObserverRegistered = false;
        }
    }

    /**
     * <p>Returns the recycler view hosted by this fragment.</p>
     * <p>Note : you <b>must</b> add an adapter to this recycler view with setAdapter(Adapter)
     * instead of using RecyclerView#setAdapter(Adapter) directly.</p>
     *
     * @return the recycler view hosted by this fragment
     */
    public RecyclerView getRecyclerView() {
        ensureRecycler();
        return mRecycler;
    }

    /**
     * <p>Control whether the recycler view is beeing displayed.
     * You can make it not displayed if you are waiting for the initial data to be available.
     * During this time an indeterminate progress indicator will be shown instead.</p>
     * <p>Note that the visibility change of the recycler view is not immediate: when hiding the
     * recycler view by passing false to this method {@code false},
     * the progress indicator will be shown only if setRecyclerShown(true) is called after
     * a minimum (perceivable) delay. Also, if the progress indicator is shown it stays visible a
     * sufficient amount of time before changing back to a recycler view to avoid "flashes" in the UI.</p>
     *
     * @param shown if {@code true} the recycler view is shown,
     *              if {@code false} the progress indicator
     * @throws IllegalStateException if called before content view has been created
     */
    public void setRecyclerShown(boolean shown) {
        ensureRecycler();

        if (shown) {
            mDismissed = true;
            mPostedHide = false;
            mHandler.removeCallbacks(mDelayedHide);
            long diff = System.currentTimeMillis() - mStartTime;
            if (diff >= MIN_SHOW_TIME || mStartTime == -1) {
                setRecyclerShownImmediate(true);
            } else {
                if (!mPostedShow) {
                    mHandler.postDelayed(mDelayedShow, MIN_SHOW_TIME - diff);
                    mPostedShow = true;
                }
            }
        } else {
            mStartTime = -1;
            mDismissed = false;
            mPostedShow = false;
            mHandler.removeCallbacks(mDelayedShow);
            if (!mPostedHide) {
                mHandler.postDelayed(mDelayedHide, MIN_DELAY);
                mPostedHide = true;
            }
        }
    }

    private void setRecyclerShownImmediate(boolean shown) {
        if (shown) {
            mRecyclerContainer.setVisibility(View.VISIBLE);
            mProgress.setVisibility(View.GONE);
        } else {
            mRecyclerContainer.setVisibility(View.GONE);
            mProgress.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Get the adapter associated with this fragment's RecyclerView.
     */
    public Adapter<? extends ViewHolder> getAdapter() {
        return mAdapter;
    }

    /**
     * Sets the adapter for the RecyclerView hosted by this fragment.
     * If the recycler view was hidden and had no adapter, it will be shown.
     *
     * @param adapter the adapter to be associated with this fragment's RecyclerView
     */
    public void setAdapter(@Nullable Adapter<? extends ViewHolder> adapter) {
        boolean hadAdapter = mAdapter != null;

        unregisterEmptyStateObserver(mAdapter);
        registerEmptyStateObserver(adapter);

        mAdapter = adapter;
        if (mRecycler != null) {
            mRecycler.setAdapter(adapter);
            if (!hadAdapter) {
                // The list was hidden, and previously didn't have an adapter.
                // It is now time to show it.
                setRecyclerShown(true);
            }
        }
    }

    /**
     * Check that the view hierarchy provided for this fragment contains at least
     * a recycler view and a progress bar, then if so configure the recycler view
     * with the provided layout manager and adapter.
     */
    private void ensureRecycler() {
        if (mRecycler != null) {
            return;
        }

        View root = getView();
        if (root == null) {
            throw new IllegalStateException("Content view not yet created");
        }

        mProgress = root.findViewById(R.id.progress);
        if (mProgress == null) {
            throw new RuntimeException("Your content must have a View with id 'R.id.progress' " +
                    "to be displayed when RecyclerView is not shown");
        }

        mRecyclerContainer = root.findViewById(R.id.recycler_container);
        if (mRecyclerContainer == null) {
            throw new RuntimeException("Your content must have a ViewGroup " +
                    "whose id attribute is 'R.id.recycler_container'");
        }

        View rawRecycler = root.findViewById(R.id.recycler);
        if (!(rawRecycler instanceof RecyclerView)) {
            if (mRecycler == null) {
                throw new RuntimeException("Your content must have a RecyclerView " +
                        "whose id attribute is 'R.id.recycler' and child of R.id.recycler_container");
            }
            throw new RuntimeException("Content has view with id attribute 'R.id.recycler'" +
                    "that is not a RecyclerView");
        }

        mRecycler = (RecyclerView) rawRecycler;
        mEmptyView = root.findViewById(R.id.empty);

        if (mAdapter != null) {
            // If adapter is already provided, show the recycler view
            Adapter<? extends ViewHolder> adapter = mAdapter;
            mAdapter = null;
            setAdapter(adapter);
            setEmptyShown(adapter.getItemCount() == 0);
        } else {
            // We are starting without an adapter, so assume we won't
            // have our data right away and start with the progress indicator.
            setRecyclerShown(false);
            setEmptyShown(false);
        }
    }
}
