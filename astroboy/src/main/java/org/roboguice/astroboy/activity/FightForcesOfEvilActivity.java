package org.roboguice.astroboy.activity;

import android.app.Activity;
import android.view.animation.AnimationUtils;
import butterknife.Bind;
import butterknife.ButterKnife;

import org.roboguice.astroboy.R;

import roboguice.RoboGuice;
import android.os.Bundle;
import android.view.animation.Animation;
import android.widget.TextView;

/**
 * Things you'll learn in this class:
 *     - How to inject Resources
 *     - How to use RoboAsyncTask to do background tasks with injection
 *     - What it means to be a @Singleton
 */
public class FightForcesOfEvilActivity extends Activity {

    @Bind(R.id.expletive) TextView expletiveText;

    Animation expletiveAnimation;

    // AstroboyRemoteControl is annotated as @ContextSingleton, so the instance
    // we get in FightForcesOfEvilActivity will be a different instance than
    // the one we got in AstroboyMasterConsole
    //@Inject AstroboyRemoteControl remoteControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fight_evil);

        ButterKnife.bind(this);
        expletiveAnimation = AnimationUtils.loadAnimation(this, R.anim.expletive_animation);
        expletiveText.setAnimation(expletiveAnimation);
        expletiveAnimation.start();

        // Throw some punches
        for( int i=0; i<10; ++i )
            new AsyncPunch(this) {
                @Override
                protected void onSuccess(String expletive) throws Exception {
                    expletiveText.setText(expletive);
                }

                // We could also override onException() and onFinally() if we wanted
                
            }.execute();

    }

    @Override
    protected void onDestroy() {
        RoboGuice.destroyInjector(this);
        super.onDestroy();
    }
}
