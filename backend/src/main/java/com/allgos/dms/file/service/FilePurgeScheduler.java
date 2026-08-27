package com.allgos.dms.file.service;

import com.allgos.dms.file.entity.FileDeletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Once a day, permanently removes every document that has sat deleted for longer than
 * {@link FileDeletion#PURGE_RETENTION} with nobody restoring it.
 *
 * <p>3am rather than any time during the working day: a restore racing the sweep for the same file
 * is a fight nobody should have to think about, and the office is not open to have that race happen
 * in the first place at that hour.
 */
@Component
public class FilePurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(FilePurgeScheduler.class);

    private final FileService fileService;

    public FilePurgeScheduler(FileService fileService) {
        this.fileService = fileService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void sweep() {
        int purged = fileService.purgeExpired();
        if (purged > 0) {
            log.info(
                    "Permanently removed {} document(s) deleted more than {} days ago",
                    purged,
                    FileDeletion.PURGE_RETENTION.toDays());
        }
    }
}
