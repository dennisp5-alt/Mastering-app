package com.dennis.aimasteringlab;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Presentation only: native studio surfaces. Contains no audio processing. */
final class StudioVisuals {
    static final int BG=Color.rgb(5,16,29), PANEL=Color.rgb(12,36,54), CYAN=Color.rgb(35,227,235);
    static final int TEXT=Color.rgb(245,250,255), MUTED=Color.rgb(165,188,208), RED=Color.rgb(255,99,124);
    static final int GREEN=Color.rgb(51,224,190);
    private StudioVisuals(){}
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(int start,int end,int stroke,float radius,Context c){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        d.setCornerRadius(dp(c,radius));if(stroke!=Color.TRANSPARENT)d.setStroke(dp(c,1),stroke);return d;
    }
    static TextView label(Context c,String message,int size,int color,boolean bold){
        TextView v=new TextView(c);v.setText(message);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
    static void header(Context c,LinearLayout root){
        LinearLayout h=new LinearLayout(c);h.setOrientation(LinearLayout.HORIZONTAL);h.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(c);logo.setImageResource(R.drawable.ic_launcher);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(c,48),dp(c,48));ip.setMargins(0,0,dp(c,12),0);h.addView(logo,ip);
        LinearLayout t=column(c);t.addView(label(c,"DENNIS AI MASTERING LAB",18,TEXT,true));
        t.addView(label(c,"Studio workspace  •  v"+StudioQcReporting.APP_VERSION+"  •  offline",11,MUTED,false));
        h.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(c,64));hp.setMargins(0,0,0,dp(c,9));root.addView(h,hp);
        View line=new View(c);line.setBackgroundColor(Color.rgb(18,65,85));root.addView(line,new LinearLayout.LayoutParams(-1,dp(c,1)));
    }
    static View tile(Context c,String name,String description,boolean primary,boolean danger,Runnable click){
        LinearLayout outer=new LinearLayout(c);outer.setOrientation(LinearLayout.HORIZONTAL);outer.setGravity(Gravity.CENTER_VERTICAL);
        int stroke=danger?RED:primary?CYAN:Color.rgb(35,91,115);
        outer.setBackground(surface(danger?0x552D1024:primary?0xD80C6279:0xE3132B40,
                danger?0xAA311822:primary?0xFF07374D:0xFF0A1C2E,stroke,13,c));
        outer.setPadding(dp(c,12),dp(c,13),dp(c,10),dp(c,13));
        TextView glyph=label(c,symbol(name),23,danger?RED:CYAN,true);glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(surface(0xFF08283B,0xFF071728,Color.rgb(24,91,119),50,c));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(c,38),dp(c,38));gp.setMargins(0,0,dp(c,10),0);outer.addView(glyph,gp);
        LinearLayout words=column(c);TextView title=label(c,name,13,danger?RED:TEXT,true);title.setMaxLines(2);words.addView(title);
        if(description!=null&&!description.isEmpty()){
            TextView sub=label(c,description,11,MUTED,false);sub.setMaxLines(2);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(c,3);words.addView(sub,sp);
        }
        outer.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=label(c,"›",25,danger?RED:MUTED,false);outer.addView(arrow);
        outer.setClickable(true);outer.setFocusable(true);outer.setOnClickListener(v->click.run());return outer;
    }
    static String symbol(String name){
        String n=name.toUpperCase(java.util.Locale.US);
        if(n.contains("DELETE")||n.contains("REMOVE"))return "×";
        if(n.contains("RENDER")||n.contains("MASTER QUEUED")||n.contains("PLAY"))return "▶";
        if(n.contains("IMPORT")||n.contains("ADD"))return "+";
        if(n.contains("REFRESH")||n.contains("RETRY"))return "↻";
        if(n.contains("UNDO")||n.contains("BACK"))return "↶";
        if(n.contains("SAVE")||n.contains("EXPORT"))return "↓";
        if(n.contains("ALBUM"))return "♫";
        if(n.contains("REFERENCE")||n.contains("REVIEW"))return "≋";
        if(n.contains("EDIT")||n.contains("BASS"))return "≀";
        if(n.contains("QUEUE"))return "☷";
        return "◇";
    }
    static void metric(Context c,LinearLayout row,String title,String value,int color){
        LinearLayout box=column(c);box.setPadding(dp(c,11),dp(c,11),dp(c,8),dp(c,11));
        box.setBackground(surface(0xEC122D42,0xFA081E30,0xFF24607A,12,c));
        TextView small=label(c,title,10,MUTED,false);small.setMaxLines(2);box.addView(small);
        TextView big=label(c,value,17,color,true);big.setSingleLine();box.addView(big);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(c,67),1);bp.setMargins(dp(c,2),0,dp(c,2),0);row.addView(box,bp);
    }
    static void notice(Context c,LinearLayout root,String headline,String detail,boolean warning){
        LinearLayout box=column(c);box.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
        box.setBackground(surface(warning?0xEE321322:0xE6084D50,warning?0xE8191524:0xEC062C40,
                warning?RED:GREEN,12,c));
        box.addView(label(c,(warning?"!  ":"✓  ")+headline,15,warning?RED:TEXT,true));
        if(detail!=null&&!detail.isEmpty()){TextView d=label(c,detail,12,MUTED,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,5);box.addView(d,lp);}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,9),0,0);root.addView(box,p);
    }
    static void section(Context c,LinearLayout root,String title){
        TextView v=label(c,title,20,TEXT,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,15),0,dp(c,10));root.addView(v,p);
    }
}
