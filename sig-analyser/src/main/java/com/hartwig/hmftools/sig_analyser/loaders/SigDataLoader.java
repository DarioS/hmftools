package com.hartwig.hmftools.sig_analyser.loaders;

import static com.hartwig.hmftools.sig_analyser.SigAnalyser.LOG_DEBUG;
import java.sql.SQLException;

import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class SigDataLoader
{
    // config
    private static final String LOAD_SNVS = "load_snvs";
    private static final String LOAD_MNVS = "load_mnvs";
    private static final String LOAD_INDELS = "load_indels";
    private static final String DB_USER = "db_user";
    private static final String DB_PASS = "db_pass";
    private static final String DB_URL = "db_url";

    private static final Logger LOGGER = LogManager.getLogger(SigDataLoader.class);

    public static void main(@NotNull final String[] args) throws ParseException
    {
        Options options = createBasicOptions();

        // SigSnvLoader.addCmdLineArgs(options);
        DataLoaderConfig.addCmdLineArgs(options);

        final CommandLine cmd = createCommandLine(args, options);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }

        final DataLoaderConfig config = new DataLoaderConfig(cmd);

        try
        {
            final DatabaseAccess dbAccess = databaseAccess(cmd);

            config.loadSampleIds(dbAccess);

            if(cmd.hasOption(LOAD_SNVS))
            {
                SigSnvLoader snvLoader = new SigSnvLoader(config);
                snvLoader.loadData(dbAccess);
            }

            if(cmd.hasOption(LOAD_MNVS))
            {
                SigMnvLoader snvLoader = new SigMnvLoader(config);
                snvLoader.loadData(dbAccess);
            }

            if(cmd.hasOption(LOAD_INDELS))
            {
                SigIndelLoader snvLoader = new SigIndelLoader(config);
                snvLoader.loadData(dbAccess);
            }
        }
        catch(SQLException e)
        {
            LOGGER.error("DB connection failed: {}", e.toString());
        }

        LOGGER.info("data load complete");
    }

    @NotNull
    private static Options createBasicOptions()
    {
        Options options = new Options();

        options.addOption(LOAD_SNVS, false, "Create sample bucket counts for SNVs from DB");
        options.addOption(LOAD_MNVS, false, "Create sample bucket counts for MNVs from DB");
        options.addOption(LOAD_INDELS, false, "Create sample bucket counts for INDELs from DB");

        options.addOption(DB_USER, true, "Database user name");
        options.addOption(DB_PASS, true, "Database password");
        options.addOption(DB_URL, true, "Database url");

        options.addOption(LOG_DEBUG, false, "Sets log level to Debug, off by default");

        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    @NotNull
    private static DatabaseAccess databaseAccess(@NotNull final CommandLine cmd) throws SQLException
    {
        final String userName = cmd.getOptionValue(DB_USER);
        final String password = cmd.getOptionValue(DB_PASS);
        final String databaseUrl = cmd.getOptionValue(DB_URL);
        final String jdbcUrl = "jdbc:" + databaseUrl;
        return new DatabaseAccess(userName, password, jdbcUrl);
    }

}
