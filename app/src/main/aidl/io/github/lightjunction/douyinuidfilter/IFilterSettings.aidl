package io.github.lightjunction.douyinuidfilter;

import android.os.Bundle;

interface IFilterSettings {
    Bundle read();
    void report(in Bundle diagnostics);
}
