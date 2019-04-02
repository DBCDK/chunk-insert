
/*
 * Copyright (C) 2018 DBC A/S (http://dbc.dk/)
 *
 * This is part of queue-all
 *
 * queue-all is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * queue-all is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc.inserts;

import dk.dbc.ExitException;
import dk.dbc.ReThrowException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Morten Bøgeskov (mb@dbc.dk)
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            Arguments arguments = new Arguments(args);
            log.info("chunk-insert");
            try {
                new ChunkInsert().run(arguments);
            } catch (ReThrowException ex) {
                ex.throwAs(ExitException.class);
                ex.throwAs(SQLException.class);
                throw ex;
            }
        } catch (ExitException ex) {
            System.exit(ex.getExitCode());
        } catch (Exception ex) {
            log.error("Error: {}", ex.getMessage());
            log.debug("Error: ", ex);
            System.exit(1);
        }
    }
}
