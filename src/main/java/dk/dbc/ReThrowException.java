/*
 * Copyright (C) 2019 DBC A/S (http://dbc.dk/)
 *
 * This is part of sql-insert
 *
 * sql-insert is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sql-insert is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc;

/**
 *
 * @author Morten Bøgeskov (mb@dbc.dk)
 */
public class ReThrowException extends RuntimeException {

    private static final long serialVersionUID = 7065659902551397272L;

    private ReThrowException(Throwable t) {
        super(t);
    }

    @Override
    public String getMessage() {
        for (Throwable t = getCause() ; t != null ; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null)
                return message;
        }
        return "anonymous";
    }

    /**
     * If any cause is of a type throw that cause
     *
     * @param <T>   Type of cause you want to extract
     * @param clazz cause class
     * @throws T if a cause matched (inherit)
     */
    public <T extends Exception> void throwAs(Class<?> clazz) throws T {
        for (Throwable t = getCause() ; t != null ; t = t.getCause()) {
            if (clazz.isAssignableFrom(t.getClass()))
                throw (T) t;
        }
    }

    /**
     * Wrap a piece of code, ensuring only runtime exceptions are cast
     *
     * @param block block of code withe out a return value
     * @throws ReThrowException if a non runtime exception occurred in block
     */
    public static void wrap(VoidBlock block) throws ReThrowException {
        try {
            block.accept();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ReThrowException(ex);
        }
    }

    /**
     * Wrap a piece of code, ensuring only runtime exceptions are cast
     *
     * @param <V>   return type of code block
     * @param block block of code returning a value
     * @return the value from the block
     * @throws ReThrowException if a non runtime exception occurred in block
     */
    public static <V> V wrap(ValueBlock<V> block) throws ReThrowException {
        try {
            return block.accept();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ReThrowException(ex);
        }
    }

    @FunctionalInterface
    public interface VoidBlock {

        void accept() throws Exception;
    }

    @FunctionalInterface
    public interface ValueBlock<V> {

        V accept() throws Exception;
    }
}
