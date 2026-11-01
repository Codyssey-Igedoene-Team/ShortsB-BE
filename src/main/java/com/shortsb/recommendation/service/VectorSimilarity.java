package com.shortsb.recommendation.service;

import com.shortsb.recommendation.repository.RecommendationRepository;
import java.util.ArrayList;
import java.util.List;

// 사전 벡터의 코사인 유사도와 페이지 평균 벡터
public final class VectorSimilarity {

    private VectorSimilarity() {}

    public static double cosine(List<Double> left, List<Double> right) {
        if (left.size() != right.size() || left.isEmpty()) {
            throw RecommendationRepository.notReady();
        }
        double dot = 0;
        double a = 0;
        double b = 0;
        for (int index = 0; index < left.size(); index++) {
            double x = left.get(index);
            double y = right.get(index);
            dot += x * y;
            a += x * x;
            b += y * y;
        }
        double value = dot / (Math.sqrt(a) * Math.sqrt(b));
        if (!Double.isFinite(value)) {
            throw RecommendationRepository.notReady();
        }
        return Math.max(-1, Math.min(1, value));
    }

    public static List<Double> mean(List<List<Double>> vectors) {
        if (vectors.isEmpty() || vectors.getFirst().isEmpty()) {
            throw RecommendationRepository.notReady();
        }
        double[] sum = new double[vectors.getFirst().size()];
        for (List<Double> vector : vectors) {
            if (vector.size() != sum.length) {
                throw RecommendationRepository.notReady();
            }
            for (int index = 0; index < sum.length; index++) {
                sum[index] += vector.get(index) / vectors.size();
            }
        }
        double norm = 0;
        for (double value : sum) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (!Double.isFinite(norm) || norm < 1e-12) {
            throw RecommendationRepository.notReady();
        }
        List<Double> result = new ArrayList<>(sum.length);
        for (double value : sum) {
            result.add(value / norm);
        }
        return List.copyOf(result);
    }
}
