package org.roboguice.astroboy.activity;

import android.content.Context;
import java.util.Random;
import javax.inject.Inject;
import org.roboguice.astroboy.controller.Astroboy;
import roboguice.util.RoboAsyncTask;

/**
* Created by administrateur on 15-07-20.
*/ // This class will call Astroboy.punch() in the background
public class AsyncPunch extends RoboAsyncTask<String> {

    // Because Astroboy is a @Singleton, this will be the same
    // instance that we inject elsewhere in our app.
    // Random of course will be a new instance of java.util.Random, since
    // we haven't specified any special binding instructions anywhere
    @Inject Astroboy astroboy;
    @Inject Random random;

    public AsyncPunch(Context context) {
        super(context);
    }

    public String call() throws Exception {
        Thread.sleep(random.nextInt(5*1000));
        return astroboy.punch();
    }
}
