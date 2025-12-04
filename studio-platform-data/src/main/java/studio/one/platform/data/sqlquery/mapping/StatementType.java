/*
 * Copyright 2016 donghyuck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package studio.one.platform.data.sqlquery.mapping;

public enum StatementType {

    /**
     * @uml.property name="sTATEMENT"
     * @uml.associationEnd
     */
    STATEMENT,
    /**
     * @uml.property name="pREPARED"
     * @uml.associationEnd
     */
    PREPARED,
    /**
     * @uml.property name="fUNCTION"
     * @uml.associationEnd
     */
    FUNCTION,
    /**
     * @uml.property name="cALLABLE"
     * @uml.associationEnd
     */
    CALLABLE

}
