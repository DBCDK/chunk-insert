/*
 * Copyright (C) 2019 DBC A/S (http://dbc.dk/)
 *
 * This is part of foreach-sql
 *
 * foreach-sql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * foreach-sql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc.inserts;

import dk.dbc.ExitException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.*;

/**
 *
 * @author Morten Bøgeskov (mb@dbc.dk)
 */
public final class Arguments {

    private final Options options;
    private final Option help;
    private final Option verbose;
    private final Option commit;
    private final Option db;
    private final Option quiet;

    private CommandLine commandLine;
    private String sql;

    public Arguments(String... args) throws ExitException {
        this.options = new Options()
                .addOption(this.help = Option.builder("h")
                        .longOpt("help")
                        .desc("this help")
                        .build())
                .addOption(this.verbose = Option.builder("v")
                        .longOpt("verbose")
                        .desc("debug log")
                        .build())
                .addOption(this.quiet = Option.builder("q")
                        .longOpt("quiet")
                        .desc("log only errors")
                        .build())
                .addOption(this.commit = Option.builder("c")
                        .longOpt("commit")
                        .hasArg()
                        .argName("N")
                        .desc("Commit for every N rows")
                        .build())
                .addOption(this.db = Option.builder("d")
                        .longOpt("database")
                        .hasArg()
                        .required()
                        .argName("DB")
                        .desc("Database url")
                        .build());
        parse(args);
    }

    private String getOpt(Option option, String defaultValue) {
        return commandLine.getOptionValue(option.getOpt(), defaultValue);
    }

    public String getDb() {
        return getOpt(db, null);
    }

    public int getCommit() {
        return Integer.parseUnsignedInt(getOpt(commit, "5000"));
    }

    public String getSql() {
        return sql;
    }

    private void addPositionalArguments() {
        options.addOption(positionalArgument(true, "SQL", "sql statement of the type 'INSERT INTO xxx (...) SELECT ...'"));
    }

    private void acquirePositionalArguments(Iterator<String> args, Stream.Builder<String> missing) {
        if (args.hasNext())
            this.sql = args.next();
        else
            missing.accept("SQL");
    }

    private Arguments parse(String... args) throws ExitException {
        try {

            Stream.Builder<String> missing = parseAsNonRequired(args, help);

            setupLogLevel("dk.dbc");

            Iterator<String> extra = commandLine.getArgList().iterator();

            acquirePositionalArguments(extra, missing);

            if (extra.hasNext())
                throw usage("Too many arguments");

            String missingRequired = missing.build()
                    .collect(joining(", "))
                    .replaceFirst("\\(.*\\), ", "\\1 & ");

            if (!missingRequired.isEmpty())
                throw usage("Missing required options: " + missingRequired);

            try {
                if (getCommit() == 0)
                    throw usage("0 is not a valid number for commit");
            } catch (NumberFormatException ex) {
                throw usage("'" + getOpt(commit, "") + "' is not a valid number for commit");
            }

        } catch (ParseException ex) {
            throw usage(ex.getMessage());
        }
        return this;
    }

    private void setupLogLevel(String... packages) throws ExitException {
        boolean v = commandLine.hasOption(verbose.getOpt());
        boolean q = commandLine.hasOption(quiet.getOpt());

        if (!v && !q)
            return;
        if (v && q)
            throw usage("You cannot have both -q and -v");
        Level level = Level.INFO;
        if (v)
            level = Level.DEBUG;
        if (q)
            level = Level.WARN;
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (String pkg : packages) {
            context.getLogger(pkg).setLevel(level);
        }
    }

    private Stream.Builder<String> parseAsNonRequired(String[] args, Option helpOption) throws ParseException, ExitException {
        List<Option> required = options.getOptions().stream()
                .filter(Option::isRequired)
                .collect(toList());
        required.forEach(r -> r.setRequired(false));
        Options nonRequired = new Options();
        options.getOptions().forEach(nonRequired::addOption);
        this.commandLine = new DefaultParser().parse(nonRequired, args);
        required.forEach(r -> r.setRequired(true));

        if (commandLine.hasOption(helpOption.getOpt()))
            throw usage("");

        Stream.Builder<String> errors = Stream.builder();

        required.stream()
                .map(Option::getOpt)
                .filter(opt -> !commandLine.hasOption(opt))
                .forEach(errors::accept);
        return errors;
    }

    public ExitException usage(String error) {

        addPositionalArguments();

        boolean hasError = error != null && !error.isEmpty();
        OutputStream os = hasError ? System.err : System.out;
        try (Writer osWriter = new OutputStreamWriter(os, StandardCharsets.UTF_8) ;
             PrintWriter writer = new PrintWriter(osWriter)) {
            HelpFormatter formatter = new HelpFormatter();
            if (hasError) {
                formatter.printWrapped(writer, 76, error);
                formatter.printWrapped(writer, 76, "");
            }
            formatter.printUsage(writer, 76, executable(), options);
            formatter.printWrapped(writer, 76, "");
            formatter.printOptions(writer, 76, options, 4, 4);
            formatter.printWrapped(writer, 76, "");
            os.flush();
        } catch (IOException ex) {
            System.err.println(ex);
            hasError = true;
        }
        if (hasError)
            return new ExitException(1);
        return new ExitException(0);
    }

    private static char c = '\u00e0'; // Positional late in the alphabet.

    private static Option positionalArgument(boolean required, String argName, String description) {
        return new Option(String.valueOf(++c), description) {
            private static final long serialVersionUID = 7186966870093513714L;

            @Override
            public String getArgName() {
                return argName;
            }

            @Override
            public boolean hasArg() {
                return true;
            }

            @Override
            public boolean hasLongOpt() {
                return false;
            }

            @Override
            public boolean isRequired() {
                return required;
            }

            @Override
            public String getDescription() {
                return "    " + super.getDescription();
            }

            @Override
            public String getOpt() {
                return "\010\010";
            }
        };
    }

    private static String executable() {
        try {
            return "java -jar " +
                   new java.io.File(Arguments.class.getProtectionDomain()
                           .getCodeSource()
                           .getLocation()
                           .toURI()
                           .getPath())
                           .getName();
        } catch (RuntimeException | URISyntaxException ex) {
            return "[executable]";
        }
    }

}
