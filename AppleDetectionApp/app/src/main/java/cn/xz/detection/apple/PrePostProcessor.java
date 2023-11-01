// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package cn.xz.detection.apple;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;


public class PrePostProcessor {
    // for yolo model, no need to apply MEAN and STD
    static float[] NO_MEAN_RGB = new float[]{0.0f, 0.0f, 0.0f};
    static float[] NO_STD_RGB = new float[]{1.0f, 1.0f, 1.0f};

    // model input image size
    static int mInputWidth = 640;
    static int mInputHeight = 640;

    private static final int NUM_OF_CLASSES = 1;
    // check export command output
    // PyTorch: starting from 'apple_detection_best.pt' with input shape (1, 3, 640, 640) BCHW and output shape(s) (1, 5, 8400) (6.0 MB)
    // mOutputColumn = 5, mOutputRow = 5 * 8400
    private static int mOutputRow = 42000;
    private static int mOutputColumn = 5;
    private static float confThreshold = 0.7f; // score above which a detection is generated
    private static float iouThreshold = 0.7f;
    private static int mNmsLimit = 15;

    static String[] mClasses = new String[]{"apple"};

    // The two methods nonMaxSuppression and IOU below are ported from https://github.com/hollance/YOLO-CoreML-MPSNNGraph/blob/master/Common/Helpers.swift

    /**
     * Removes bounding boxes that overlap too much with other boxes that have
     * a higher score.
     * - Parameters:
     * - boxes: an array of bounding boxes and their scores
     * - limit: the maximum number of boxes that will be selected
     * - threshold: used to decide whether boxes overlap too much
     */
    static ArrayList<Result> nonMaxSuppression(ArrayList<Result> boxes, int limit, float threshold) {

        // Do an argsort on the confidence scores, from high to low.
        Collections.sort(boxes,
                new Comparator<Result>() {
                    @Override
                    public int compare(Result o1, Result o2) {
                        return o1.score.compareTo(o2.score);
                    }
                });

        ArrayList<Result> selected = new ArrayList<>();
        boolean[] active = new boolean[boxes.size()];
        Arrays.fill(active, true);
        int numActive = active.length;

        // The algorithm is simple: Start with the box that has the highest score.
        // Remove any remaining boxes that overlap it more than the given threshold
        // amount. If there are any boxes left (i.e. these did not overlap with any
        // previous boxes), then repeat this procedure, until no more boxes remain
        // or the limit has been reached.
        boolean done = false;
        for (int i = 0; i < boxes.size() && !done; i++) {
            if (active[i]) {
                Result boxA = boxes.get(i);
                selected.add(boxA);
                if (selected.size() >= limit) break;

                for (int j = i + 1; j < boxes.size(); j++) {
                    if (active[j]) {
                        Result boxB = boxes.get(j);
                        if (IOU(boxA.rect, boxB.rect) > threshold) {
                            active[j] = false;
                            numActive -= 1;
                            if (numActive <= 0) {
                                done = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return selected;
    }

    /**
     * Computes intersection-over-union overlap between two bounding boxes.
     */
    static float IOU(Rect a, Rect b) {
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        if (areaA <= 0.0) return 0.0f;

        float areaB = (b.right - b.left) * (b.bottom - b.top);
        if (areaB <= 0.0) return 0.0f;

        float intersectionMinX = Math.max(a.left, b.left);
        float intersectionMinY = Math.max(a.top, b.top);
        float intersectionMaxX = Math.min(a.right, b.right);
        float intersectionMaxY = Math.min(a.bottom, b.bottom);
        float intersectionArea = Math.max(intersectionMaxY - intersectionMinY, 0) *
                Math.max(intersectionMaxX - intersectionMinX, 0);
        return intersectionArea / (areaA + areaB - intersectionArea);
    }

    // 不同的模型返回的格式可能不一样，此处返回的是 5 * 8400，展成以为数据就变成了 [x1, x2,...x8400, y1, y2,..., w1, w2,..., h1, h2,..., score1, score2,...]
    static ArrayList<Result> outputsToNMSPredictions(float[] outputs, float imgScaleX, float imgScaleY, float ivScaleX, float ivScaleY, float startX, float startY) {
        ArrayList<Result> results = new ArrayList<>();
        int recordCount = mOutputRow / mOutputColumn;
        // TODO remove test
        for (int i = 0; i < recordCount; i++) {
            List<Float> scores = new LinkedList<>();
            scores.add(outputs[i + recordCount * 4]);
            Collections.sort(scores);
        }
        for (int i = 0; i < recordCount; i++) {
            // <x-center>, <y-center>, <width>, <height>
            float x = outputs[i];
            float y = outputs[i + recordCount];
            float w = outputs[i + recordCount * 2];
            float h = outputs[i + recordCount * 3];
            float score = outputs[i + recordCount * 4];
            if (score > confThreshold) {
                float left = imgScaleX * (x - w / 2);
                float top = imgScaleY * (y - h / 2);
                float right = imgScaleX * (x + w / 2);
                float bottom = imgScaleY * (y + h / 2);

                // 当前的返回格式，第五个就是score
//                float max = outputs[i* mOutputColumn +5];
//                int cls = 0;
//                for (int j = 0; j < mOutputColumn -5; j++) {
//                    if (outputs[i* mOutputColumn +5+j] > max) {
//                        max = outputs[i* mOutputColumn +5+j];
//                        cls = j;
//                    }
//                }
                float max = outputs[i * mOutputColumn + 4];
                int cls = 0;

                Rect rect = new Rect((int) (startX + ivScaleX * left), (int) (startY + top * ivScaleY), (int) (startX + ivScaleX * right), (int) (startY + ivScaleY * bottom));
                Result result = new Result(cls, score, rect);
                results.add(result);
            }
        }
        return nonMaxSuppression(results, mNmsLimit, iouThreshold);
    }

    public static class Result {
        int classIndex;
        Float score;
        Rect rect;

        public Result(int cls, Float output, Rect rect) {
            this.classIndex = cls;
            this.score = output;
            this.rect = rect;
        }
    }
}
