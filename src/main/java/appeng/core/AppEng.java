/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core;


import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.google.common.base.Stopwatch;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

import appeng.core.crash.CrashInfo;
import appeng.core.crash.IntegrationCrashEnhancement;
import appeng.core.crash.ModCrashEnhancement;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.worlddata.WorldData;
import appeng.hooks.TickHandler;
import appeng.integration.IntegrationRegistry;
import appeng.server.AECommand;
import appeng.services.VersionChecker;
import appeng.services.version.VersionCheckerConfig;
import appeng.util.Platform;


@Mod( modid = AppEng.MOD_ID, acceptedMinecraftVersions = "[1.7.10]", name = AppEng.MOD_NAME, version = AEConfig.VERSION, dependencies = AppEng.MOD_DEPENDENCIES, guiFactory = "appeng.client.gui.config.AEConfigGuiFactory" )
public final class AppEng
{
	public static final String MOD_ID = "appliedenergistics2";
	public static final String MOD_NAME = "Applied Energistics 2";
	public static final String MOD_DEPENDENCIES =
			// a few mods, AE should load after, probably.
			// required-after:AppliedEnergistics2API|all;
			// "after:gregtech_addon;after:Mekanism;after:IC2;after:ThermalExpansion;after:BuildCraft|Core;" +

			// depend on version of forge used for build.
			"after:appliedenergistics2-core;" + "required-after:Forge@[" // require forge.
					+ net.minecraftforge.common.ForgeVersion.majorVersion + '.' // majorVersion
					+ net.minecraftforge.common.ForgeVersion.minorVersion + '.' // minorVersion
					+ net.minecraftforge.common.ForgeVersion.revisionVersion + '.' // revisionVersion
					+ net.minecraftforge.common.ForgeVersion.buildVersion + ",)"; // buildVersion

	@Nonnull
	private static final AppEng INSTANCE = new AppEng();

	private final IMCHandler imcHandler;

	private File configDirectory;

	AppEng()
	{
		this.imcHandler = new IMCHandler();

		FMLCommonHandler.instance().registerCrashCallable( new ModCrashEnhancement( CrashInfo.MOD_VERSION ) );
	}

	@Nonnull
	@Mod.InstanceFactory
	public static AppEng instance()
	{
		return INSTANCE;
	}

	public final File getConfigDirectory()
	{
		return this.configDirectory;
	}

	@EventHandler
	private void preInit( final FMLPreInitializationEvent event )
	{
		if( !Loader.isModLoaded( "appliedenergistics2-core" ) )
		{
			CommonHelper.proxy.missingCoreMod();
		}

		final Stopwatch watch = Stopwatch.createStarted();
		this.configDirectory = new File( event.getModConfigurationDirectory().getPath(), "AppliedEnergistics2" );

		final File configFile = new File( this.configDirectory, "AppliedEnergistics2.cfg" );
		final File facadeFile = new File( this.configDirectory, "Facades.cfg" );
		final File versionFile = new File( this.configDirectory, "VersionChecker.cfg" );

		AEConfig.instance = new AEConfig( configFile );
		FacadeConfig.instance = new FacadeConfig( facadeFile );
		final VersionCheckerConfig versionCheckerConfig = new VersionCheckerConfig( versionFile );

		AELog.info( "Pre Initialization ( started )" );

		CreativeTab.init();
		if( AEConfig.instance.isFeatureEnabled( AEFeature.Facades ) )
		{
			CreativeTabFacade.init();
		}

		if( Platform.isClient() )
		{
			CommonHelper.proxy.init();
		}

		Registration.INSTANCE.preInitialize( event );

		if( versionCheckerConfig.isEnabled() )
		{
			final VersionChecker versionChecker = new VersionChecker( versionCheckerConfig );
			final Thread versionCheckerThread = new Thread( versionChecker );

			this.startService( "AE2 VersionChecker", versionCheckerThread );
		}

		AELog.info( "Pre Initialization ( ended after " + watch.elapsed( TimeUnit.MILLISECONDS ) + "ms )" );
	}

	private void startService( final String serviceName, final Thread thread )
	{
		thread.setName( serviceName );
		thread.setPriority( Thread.MIN_PRIORITY );

		AELog.info( "Starting " + serviceName );
		thread.start();
	}

	@EventHandler
	private void init( final FMLInitializationEvent event )
	{
		final Stopwatch star = Stopwatch.createStarted();
		AELog.info( "Initialization ( started )" );

		Registration.INSTANCE.initialize( event );
		IntegrationRegistry.INSTANCE.init();

		AELog.info( "Initialization ( ended after " + star.elapsed( TimeUnit.MILLISECONDS ) + "ms )" );
	}

	@EventHandler
	private void postInit( final FMLPostInitializationEvent event )
	{
		final Stopwatch star = Stopwatch.createStarted();
		AELog.info( "Post Initialization ( started )" );

		Registration.INSTANCE.postInit( event );
		IntegrationRegistry.INSTANCE.postInit();
		FMLCommonHandler.instance().registerCrashCallable( new IntegrationCrashEnhancement() );

		CommonHelper.proxy.postInit();
		AEConfig.instance.save();

		NetworkRegistry.INSTANCE.registerGuiHandler( this, GuiBridge.GUI_Handler );
		NetworkHandler.instance = new NetworkHandler( "AE2" );

		AELog.info( "Post Initialization ( ended after " + star.elapsed( TimeUnit.MILLISECONDS ) + "ms )" );
	}

	@EventHandler
	private void handleIMCEvent( final FMLInterModComms.IMCEvent event )
	{
		this.imcHandler.handleIMCEvent( event );
	}

	@EventHandler
	private void serverAboutToStart( final FMLServerAboutToStartEvent evt )
	{
		WorldData.onServerAboutToStart();
	}

	@EventHandler
	private void serverStopping( final FMLServerStoppingEvent event )
	{
		WorldData.instance().onServerStopping();
		TickHandler.INSTANCE.shutdown();
	}

	@EventHandler
	private void serverStarting( final FMLServerStartingEvent evt )
	{
		evt.registerServerCommand( new AECommand( evt.getServer() ) );
	}
}
