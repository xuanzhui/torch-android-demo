package cn.xz.detection.apple;

import java.util.ArrayList;

public class AnalysisResult {
    public final ArrayList<PrePostProcessor.Result> mResults;

    public AnalysisResult(ArrayList<PrePostProcessor.Result> results) {
        mResults = results;
    }
}
