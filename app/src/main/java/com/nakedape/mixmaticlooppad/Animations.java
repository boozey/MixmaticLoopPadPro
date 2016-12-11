package com.nakedape.mixmaticlooppad;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Created by Nathan on 11/27/2016.
 */

public class Animations {

    public static AnimatorSet popIn(View v, int duration, int delay) {
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        ObjectAnimator expandX = ObjectAnimator.ofFloat(v, "ScaleX", 0f, 1f);
        ObjectAnimator expandY = ObjectAnimator.ofFloat(v, "ScaleY", 0f, 1f);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(expandX, expandY, fadeIn);
        set.setInterpolator(new OvershootInterpolator());
        set.setStartDelay(delay);
        set.setDuration(duration);
        return set;

    }

    public static AnimatorSet slideUp(View v, int duration, int delay, float amount){
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        ObjectAnimator slide = ObjectAnimator.ofFloat(v, "TranslationY", v.getTranslationY() + amount, v.getTranslationY());
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new DecelerateInterpolator());
        set.playTogether(slide, fadeIn);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet slideOutDown(View v, int duration, int delay, float amount){
        ObjectAnimator slide = ObjectAnimator.ofFloat(v, "TranslationY", v.getTranslationY(), v.getTranslationY() + amount);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new AccelerateInterpolator());
        set.playTogether(slide, fadeIn);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet fadeIn(View v, int duration, int delay){
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(v, "alpha",0f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new DecelerateInterpolator());
        set.play(fadeOut);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet fadeOut(View v, int duration, int delay){
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new DecelerateInterpolator());
        set.play(fadeOut);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }
}
