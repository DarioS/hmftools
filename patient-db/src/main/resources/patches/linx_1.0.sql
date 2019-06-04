
ALTER TABLE structuralVariant
    ADD COLUMN svId INT NOT NULL AFTER modified,
    CHANGE adjustedStartAF adjustedAFStart DOUBLE PRECISION NOT NULL,
    CHANGE adjustedEndAF adjustedAFEnd DOUBLE PRECISION NOT NULL,
    CHANGE adjustedStartCopyNumber adjustedCopyNumberStart DOUBLE PRECISION,
    CHANGE adjustedEndCopyNumber adjustedCopyNumberEnd DOUBLE PRECISION,
    CHANGE adjustedStartCopyNumberChange adjustedCopyNumberChangeStart DOUBLE PRECISION,
    CHANGE adjustedEndCopyNumberChange adjustedCopyNumberChangeEnd DOUBLE PRECISION,
    ADD INDEX (svId);

CREATE TABLE svLinxData
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    svId INT NOT NULL,
    clusterId INT NOT NULL,
    clusterReason VARCHAR(255) NULL,
    fragileSiteStart BOOLEAN NOT NULL,
    fragileSiteEnd BOOLEAN NOT NULL,
    isFoldback BOOLEAN NOT NULL,
    lineTypeStart VARCHAR(20),
    lineTypeEnd VARCHAR(20),
    ploidyMin DOUBLE PRECISION,
    ploidyMax DOUBLE PRECISION,
    geneStart VARCHAR(100),
    geneEnd varchar(100),
    replicationTimingStart DOUBLE PRECISION,
    replicationTimingEnd DOUBLE PRECISION,
    localTopologyIdStart INT,
    localTopologyIdEnd INT,
    localTopologyStart varchar(20),
    localTopologyEnd VARCHAR(20),
    localTICountStart INT,
    localTICountEnd INT,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

CREATE TABLE cluster
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    clusterId INT NOT NULL,
    resolvedType VARCHAR(20),
    synthetic BOOLEAN NOT NULL,
    subClonal BOOLEAN NOT NULL,
    subType VARCHAR(20),
    clusterCount INT,
    clusterDesc VARCHAR(50),
    PRIMARY KEY (id),
    INDEX(sampleId)
);

CREATE TABLE svLink
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    clusterId INT NOT NULL,
    chainId INT NOT NULL,
    chainIndex INT NOT NULL,
    chainLinkCount INT NOT NULL,
    lowerBreakendId INT NOT NULL,
    upperBreakendId INT NOT NULL,
    lowerBreakendIsStart BOOLEAN NOT NULL,
    upperBreakendIsStart BOOLEAN NOT NULL,
    arm VARCHAR(2),
    assembled BOOLEAN NOT NULL,
    traversedSVCount INT,
    linkLength INT,
    ploidy DOUBLE PRECISION,
    pseudogeneInfo varchar(255),
    PRIMARY KEY (id),
    INDEX(sampleId)
);


ALTER TABLE structuralVariantBreakend
    CHANGE isStartEnd startBreakend BOOLEAN NOT NULL,
    CHANGE isCanonicalTranscript canonicalTranscript BOOLEAN NOT NULL,
    DROP COLUMN strand
    DROP COLUMN exonRankUpstream
    DROP COLUMN exonRankDownstream
    DROP COLUMN exonPhaseUpstream
    DROP COLUMN exonPhaseDownstream
    CHANGE 
    ADD geneOrientation VARCHAR(20) NOT NULL,
    ADD disruptive BOOLEAN NOT NULL,
    ADD reportedDisruption BOOLEAN NOT NULL,
    ADD regionType VARCHAR(20) NOT NULL,
    ADD codingContext VARCHAR(20),
    ADD biotype VARCHAR(255),
    ADD exactBasePhase TINYINT,
    ADD nextSpliceExonRank TINYINT UNSIGNED,
    ADD nextSpliceExonPhase TINYINT,
    ADD nextSpliceDistance INT,
    CHANGE exonMax totalExonCount SMALLINT NOT NULL;

ALTER TABLE structuralVariantFusion
	ADD name VARCHAR(50) NOT NULL,
    CHANGE isReported reported BOOLEAN NOT NULL,
    ADD reportedType varchar(255) NULL,
    ADD chainLength INT,
    ADD skippedExons INT;

