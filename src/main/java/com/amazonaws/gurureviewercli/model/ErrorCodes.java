package com.amazonaws.gurureviewercli.model;

import lombok.Getter;

/**
 * Error Codes for the CLI.
 */
public enum ErrorCodes {

    GIT_INVALID_DIR("Invalid Git Directory"),
    GIT_BRANCH_MISSING("Cannot determine Git branch"),
    DIR_NOT_FOUND("Provided path is not a valid directory"),
    GIT_INVALID_COMMITS("Not a valid commit"),
    GIT_EMPTY_DIFF("Git Diff is empty"),
    AWS_INIT_ERROR("Failed to initialize AWS API"),
    BAD_BUCKET_NAME("CodeGuru Reviewer expects bucket names to start with codeguru-reviewer-"),
    USER_ABORT("Abort");

    @Getter
    final String errorMessage;

    ErrorCodes(String msg) {
        this.errorMessage = msg;
    }

    @Override
    public String toString() {
        return errorMessage;
    }
}
