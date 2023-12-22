package net.verdx.libstreaming;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.method.ReplacementTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.UUID;

import net.verdx.libstreaming.gui.StreamDetail;
import net.verdx.libstreaming.gui.ViewStreamActivity;
import net.verdx.libstreaming.utils.IOUtils;

import com.facebook.shimmer.ShimmerFrameLayout;

public class StreamListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_DATA = 0;
    private static final int VIEW_TYPE_PLACEHOLDER = 1;
    private final Context mContext;
    private ArrayList<StreamDetail> mStreams;

    private final Activity mActivity;

    public StreamListAdapter(Context context , ArrayList<StreamDetail> objects, Activity activity) {
        this.mStreams = objects;
        this.mContext = context;
        this.mActivity = activity;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        RecyclerView.ViewHolder holder;
        if (viewType ==  VIEW_TYPE_DATA) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.stream_detail, parent, false);
            holder = new RealViewHolder(v);
        } else {

            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.stream_detail_placeholder, parent, false);
            holder = new PlaceViewHolder(v);
        }
        return holder;
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if(holder instanceof  RealViewHolder){
            StreamDetail sd = mStreams.get(position);

            RealViewHolder realHolder = (RealViewHolder) holder;

            String[] desc = sd.getName().split("__");
            String name;
            String author;

            if(desc[0].equals("defaultName")){
                name = IOUtils.uuidToBase64(sd.getUuid());
                name = name.substring(0, name.length()-3); //Quitamos el ==\n del final que siempre esta
            }
            else {
                name = desc[0].replace("_", " ");
            }

            author = desc[1].replace("_", " ");

            realHolder.stream_name.setTransformationMethod(new WordBreakTransformationMethod());
            realHolder.stream_name.setText(name);
            realHolder.stream_author.setText(author);

            realHolder.stream_download.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(!sd.isDownload()){
                        StreamingRecord.getInstance().startStreamDownload(mContext, UUID.fromString(sd.getUuid()));
                        sd.setDownload(true);
                        Toast.makeText(mContext, "Comienza la descarga del stream seleccionado...", Toast.LENGTH_LONG).show();
                    }
                    else{
                        StreamingRecord.getInstance().stopStreamDownload(UUID.fromString(sd.getUuid()));
                        sd.setDownload(false);
                        Toast.makeText(mContext, "Finaliza la descarga del stream seleccionado...", Toast.LENGTH_SHORT).show();
                    }
                }

            });

            realHolder.stream_download.setBackgroundResource(sd.isDownload()?
                    R.drawable.button_download_pressed :
                    R.drawable.button_download);

            realHolder.stream_layout.setOnClickListener(v -> openViewStreamActivity(sd.getUuid()));
        }
        else {
            PlaceViewHolder placeHolder = (PlaceViewHolder)holder;
            placeHolder.shimmer.startShimmer();
        }


    }

    private void openViewStreamActivity(String uuid) {
        Intent streamActivityIntent = new Intent(mContext, ViewStreamActivity.class);
        streamActivityIntent.putExtra("isFromGallery", false);
        streamActivityIntent.putExtra("UUID",uuid);
        mActivity.startActivity(streamActivityIntent);
    }


    @Override
    public int getItemCount() {
        return mStreams == null? 0 : mStreams.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mStreams.get(position) == null? VIEW_TYPE_PLACEHOLDER : VIEW_TYPE_DATA;
    }

    public void setStreamsData(ArrayList<StreamDetail> data){
        mStreams = data;
        notifyDataSetChanged();
    }

    static class RealViewHolder extends RecyclerView.ViewHolder{

        TextView stream_name;
        ImageButton stream_download;
        LinearLayout stream_layout;
        TextView stream_author;

        public RealViewHolder(@NonNull View itemView) {
            super(itemView);
            stream_name = itemView.findViewById(R.id.stream_name);
            stream_author = itemView.findViewById(R.id.stream_author);
            stream_download = itemView.findViewById(R.id.downloadButton);
            stream_layout = itemView.findViewById(R.id.stream_item_list);
        }
    }

    static class PlaceViewHolder extends RecyclerView.ViewHolder{

        ShimmerFrameLayout shimmer;

        public PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            shimmer = itemView.findViewById(R.id.shimmer_view_container);
        }
    }

    private static class WordBreakTransformationMethod extends ReplacementTransformationMethod {
        private static final char[] dash = new char[]{'-', '‑'};
        private static final char[] space = new char[]{' ', '\u00A0'};
        private static final char[] slash = new char[]{'/', '∕'};

        private static final char[] original = new char[]{dash[0], space[0], slash[0]};
        private static final char[] replacement = new char[]{dash[1], space[1], slash[1]};

        @Override
        protected char[] getOriginal() {
            return original;
        }

        @Override
        protected char[] getReplacement() {
            return replacement;
        }
    }

}
