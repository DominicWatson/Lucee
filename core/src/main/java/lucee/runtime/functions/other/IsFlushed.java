/**
 * Copyright (c) 2015, Lucee Assosication Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package lucee.runtime.functions.other;

import lucee.runtime.PageContext;
import lucee.runtime.ext.function.Function;

/**
 * implements built in function isFlushed() which returns true if the response stream is committed
 */
public final class IsFlushed implements Function  {

    public static boolean call(PageContext pc) {

        return pc.getHttpServletResponse().isCommitted();
    }

}
