package com.tiltus.bevywizard.wizard;

import com.tiltus.bevywizard.project.TiltUiCompatibility;

import java.util.List;

/** A selectable TiltUI feature with related crate documentation. */
public record TiltFeature(String key, String title, String description, List<String> readmeCrates) {
    public static final List<TiltFeature> ALL = TiltUiCompatibility.FEATURES.stream()
            .map(feature -> new TiltFeature(feature.key(), feature.title(), feature.description(), feature.readmeCrates()))
            .toList();
}
