package com.eccyan.widget.sample;

import com.balysv.materialripple.MaterialRippleLayout;
import com.eccyan.widget.SpinningTabStrip;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import static com.eccyan.widget.SpinningTabStrip.*;


public class QuickContactFragment extends DialogFragment {

    private SpinningTabStrip tabs;
    private ViewPager pager;
    private ContactPagerAdapter adapter;

    public static QuickContactFragment newInstance() {
        QuickContactFragment quickContactFragment = new QuickContactFragment();
        return quickContactFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (getDialog() != null) {
            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View root = inflater.inflate(R.layout.fragment_quick_contact, container, false);

        tabs = (SpinningTabStrip) root.findViewById(R.id.tabs);
        pager = (ViewPager) root.findViewById(R.id.pager);
        adapter = new ContactPagerAdapter(getActivity());

        pager.setAdapter(adapter);

        tabs.setViewPager(pager);

        return root;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStart() {
        super.onStart();

        // change dialog width
        if (getDialog() != null) {

            int fullWidth = getDialog().getWindow().getAttributes().width;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                Display display = getActivity().getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                fullWidth = size.x;
            } else {
                Display display = getActivity().getWindowManager().getDefaultDisplay();
                fullWidth = display.getWidth();
            }

            final int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources()
                    .getDisplayMetrics());

            int w = fullWidth - padding;
            int h = getDialog().getWindow().getAttributes().height;

            getDialog().getWindow().setLayout(w, h);
        }
    }

    public static class ContactPagerAdapter extends PagerAdapter implements CustomTabProvider {

        private final int[] ICONS = {R.drawable.ic_launcher_gplus, R.drawable.ic_launcher_gmail,
                R.drawable.ic_launcher_gmaps, R.drawable.ic_launcher_chrome};
        private final Context mContext;

        public ContactPagerAdapter(Context context) {
            super();
            mContext = context;
        }

        @Override
        public int getCount() {
            return ICONS.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return super.getPageTitle(position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TextView textview= (TextView) LayoutInflater.from(mContext).inflate(R.layout.fragment_quickcontact,container,false);
            textview.setText("PAGE "+position);
            container.addView(textview);
            return textview;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object view) {
            container.removeView((View) view);
        }

        @Override
        public boolean isViewFromObject(View v, Object o) {
            return v == o;
        }

        @Override
        public View getCustomTabView(ViewGroup parent, int position) {
            MaterialRippleLayout materialRippleLayout = (MaterialRippleLayout) LayoutInflater.from(mContext).inflate(R.layout.custom_tab, parent, false);
            ((ImageView)materialRippleLayout.findViewById(R.id.image)).setImageResource(ICONS[position]);
            return materialRippleLayout;
        }
    }
}
