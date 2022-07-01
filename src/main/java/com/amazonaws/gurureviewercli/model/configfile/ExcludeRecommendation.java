package com.amazonaws.gurureviewercli.model.configfile;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class to exclude a detector in a given file.
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExcludeRecommendation {

    private String detectorId;

    private List<String> locations;

}
