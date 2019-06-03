/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.procedure.builtin.routing;

import java.util.List;

import org.neo4j.collection.RawIterator;
import org.neo4j.configuration.Config;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.Neo4jTypes;
import org.neo4j.internal.kernel.api.procs.ProcedureSignature;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.kernel.api.ResourceTracker;
import org.neo4j.kernel.api.procedure.CallableProcedure;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.kernel.database.DatabaseId;
import org.neo4j.kernel.database.DatabaseIdRepository;
import org.neo4j.procedure.Mode;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.MapValue;

import static org.neo4j.configuration.GraphDatabaseSettings.default_database;
import static org.neo4j.internal.kernel.api.procs.DefaultParameterValue.nullValue;
import static org.neo4j.internal.kernel.api.procs.ProcedureSignature.procedureSignature;
import static org.neo4j.kernel.api.exceptions.Status.Database.DatabaseNotFound;
import static org.neo4j.procedure.builtin.routing.ParameterNames.CONTEXT;
import static org.neo4j.procedure.builtin.routing.ParameterNames.DATABASE;
import static org.neo4j.procedure.builtin.routing.ParameterNames.SERVERS;
import static org.neo4j.procedure.builtin.routing.ParameterNames.TTL;

public abstract class BaseGetRoutingTableProcedure implements CallableProcedure
{
    private static final String NAME = "getRoutingTable";

    private final ProcedureSignature signature;
    private final DatabaseIdRepository databaseIdRepository;

    protected final Config config;

    protected BaseGetRoutingTableProcedure( List<String> namespace, DatabaseIdRepository databaseIdRepository, Config config )
    {
        this.signature = buildSignature( namespace );
        this.databaseIdRepository = databaseIdRepository;
        this.config = config;
    }

    @Override
    public final ProcedureSignature signature()
    {
        return signature;
    }

    @Override
    public final RawIterator<AnyValue[],ProcedureException> apply( Context ctx, AnyValue[] input, ResourceTracker resourceTracker ) throws ProcedureException
    {
        var databaseId = extractDatabaseId( input );
        var routingContext = extractRoutingContext( input );
        var result = invoke( databaseId, routingContext );
        return RawIterator.<AnyValue[],ProcedureException>of( RoutingResultFormat.build( result ) );
    }

    protected abstract String description();

    protected abstract RoutingResult invoke( DatabaseId databaseId, MapValue routingContext ) throws ProcedureException;

    protected static ProcedureException databaseNotFoundException( DatabaseId databaseId )
    {
        return new ProcedureException( DatabaseNotFound,
                "Unable to get a routing table for database '" + databaseId.name() + "' because this database does not exist" );
    }

    private DatabaseId extractDatabaseId( AnyValue[] input )
    {
        var arg = input[1];
        if ( arg == Values.NO_VALUE )
        {
            return databaseIdRepository.get( config.get( default_database ) );
        }
        else if ( arg instanceof TextValue )
        {
            return databaseIdRepository.get( ((TextValue) arg).stringValue() );
        }
        else
        {
            throw new IllegalArgumentException( "Illegal database name argument " + arg );
        }
    }

    private static MapValue extractRoutingContext( AnyValue[] input )
    {
        var arg = input[0];
        if ( arg == Values.NO_VALUE )
        {
            return MapValue.EMPTY;
        }
        else if ( arg instanceof MapValue )
        {
            return (MapValue) arg;
        }
        else
        {
            throw new IllegalArgumentException( "Illegal routing context argument " + arg );
        }
    }

    private ProcedureSignature buildSignature( List<String> namespace )
    {
        return procedureSignature( new QualifiedName( namespace, NAME ) )
                .in( CONTEXT.parameterName(), Neo4jTypes.NTMap )
                .in( DATABASE.parameterName(), Neo4jTypes.NTString, nullValue( Neo4jTypes.NTString ) )
                .out( TTL.parameterName(), Neo4jTypes.NTInteger )
                .out( SERVERS.parameterName(), Neo4jTypes.NTList( Neo4jTypes.NTMap ) )
                .mode( Mode.DBMS )
                .description( description() )
                .build();
    }
}