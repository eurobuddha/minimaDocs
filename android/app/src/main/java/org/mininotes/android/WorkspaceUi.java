package org.mininotes.android;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Shared, text-labelled controls for the approved minimaDocs workspace. */
final class WorkspaceUi {
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static TextView text(Context c,String value,int size,boolean bold){
        return Design.text(c,value,size,Design.INK(),bold?Design.sansBold():Design.sans());
    }
    static TextView note(Context c,String value){TextView v=text(c,value,13,false);v.setTextColor(Design.DIM());v.setLineSpacing(0,1.2f);return v;}
    static GradientDrawable surface(Context c,int fill,int edge){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(Design.dp(c,6));if(edge!=0)d.setStroke(Design.dp(c,1),edge);return d;}
    static TextView button(Context c,String value,boolean primary,Runnable action){
        TextView v=text(c,value,14,true);v.setGravity(Gravity.CENTER);v.setMinHeight(Design.dp(c,48));
        v.setPadding(Design.dp(c,16),Design.dp(c,10),Design.dp(c,16),Design.dp(c,10));
        v.setTextColor(primary?Design.WHITE():Design.INK());
        v.setBackground(Design.ripple(surface(c,primary?Design.ACCENT():Design.CARD(),primary?0:Design.SOFT())));
        v.setOnClickListener(w->action.run());v.setContentDescription(value);v.setFocusable(true);return v;
    }
    static void gap(LinearLayout parent,int dp){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,Design.dp(parent.getContext(),dp)));}
    static EditText search(Context c,String hint){EditText v=new EditText(c);v.setSingleLine(true);v.setTextSize(15);v.setTypeface(Design.sans());v.setTextColor(Design.INK());v.setHintTextColor(Design.DIM());v.setHint(hint);v.setContentDescription(hint);v.setPadding(Design.dp(c,16),0,Design.dp(c,16),0);v.setMinHeight(Design.dp(c,52));v.setBackground(surface(c,Design.WHITE(),Design.SOFT()));return v;}
    static void watch(EditText edit,Runnable changed){edit.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.run();}public void afterTextChanged(android.text.Editable e){}});}
}
