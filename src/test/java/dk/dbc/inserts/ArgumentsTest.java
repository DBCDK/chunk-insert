/*
 * Copyright (C) 2019 DBC A/S (http://dbc.dk/)
 *
 * This is part of chunk-insert
 *
 * chunk-insert is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * chunk-insert is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc.inserts;

import dk.dbc.ExitException;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 *
 * @author Morten Bøgeskov (mb@dbc.dk)
 */
public class ArgumentsTest {

    public ArgumentsTest() {
    }

    @Test(timeout = 2_000L, expected = ExitException.class)
    public void testMissingSql() throws Exception {
        System.out.println("testMissingSql");
        new Arguments("-d", "db");
    }

    @Test(timeout = 2_000L, expected = ExitException.class)
    public void testMissingDb() throws Exception {
        System.out.println("testMissingDb");
        new Arguments("sql-statement");
    }

    @Test(timeout = 2_000L)
    public void testCommitIntervalDefault() throws Exception {
        System.out.println("testCommitIntervalDefault");
        Arguments arguments = new Arguments("-d", "db", "sql-statement");
        assertThat(arguments.getCommit(), is(5000));
    }

    @Test(timeout = 2_000L)
    public void testCommitInterval() throws Exception {
        System.out.println("testCommitInterval");
        Arguments arguments = new Arguments("-c", "42", "-d", "db", "sql-statement");
        assertThat(arguments.getCommit(), is(42));
    }
}
