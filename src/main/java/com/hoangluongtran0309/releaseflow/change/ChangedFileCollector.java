package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;

/**
 * Asks one provider which files a change touched. Called outside any transaction, and
 * never allowed to answer "no files" when it could not ask.
 */
interface ChangedFileCollector {

    SourceType sourceType();

    ChangedFiles collect(SourceCredentials credentials, String token, int externalNumber);
}
