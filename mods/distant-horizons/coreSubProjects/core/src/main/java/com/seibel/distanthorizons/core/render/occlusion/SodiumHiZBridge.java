// AGENT-DONE(claude): sodium-gpu-hiz-occlusion
/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.seibel.distanthorizons.core.render.occlusion;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * DNCity: occlusion queries against DNCity's Sodium fork's GPU Hi-Z buffer
 * ({@code net.caffeinemc.mods.sodium.client.render.occlusion.HiZOcclusion}), looked up by reflection so DH keeps no
 * compile-time dependency on Sodium and runs unchanged without it.
 *
 * <p>Sodium reduces the vanilla terrain depth on the GPU, reads it back a few frames later without stalling and
 * reprojects it into the current camera; a query here is then a few dozen float operations on the render thread.
 * That replaces {@link BoundingBoxOcclusionCuller}'s per-section hardware queries (one draw call, uniform upload and
 * query object per section per frame, plus polling every query each frame) with no GL calls at all, which is the
 * point: less CPU and driver time per frame. The occluders are the same in both (whatever vanilla terrain is in the
 * depth buffer before DH's LOD pass), so nothing is culled that the hardware queries would not also have culled
 * up to the readback latency.
 */
public final class SodiumHiZBridge
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	private static final String CLASS_NAME = "net.caffeinemc.mods.sodium.client.render.occlusion.HiZOcclusion";

	private static boolean looked = false;
	private static MethodHandle isReady;
	private static MethodHandle isBoxOccluded;

	private SodiumHiZBridge() { }

	private static void lookup()
	{
		looked = true;
		try
		{
			Class<?> hiZ = Class.forName(CLASS_NAME);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			isReady = lookup.findStatic(hiZ, "isReadyPublic", MethodType.methodType(boolean.class));
			isBoxOccluded = lookup.findStatic(hiZ, "isBoxOccludedPublic", MethodType.methodType(boolean.class,
					double.class, double.class, double.class, double.class, double.class, double.class));
			LOGGER.info("Using Sodium's GPU Hi-Z buffer for LOD occlusion culling.");
		}
		catch (ClassNotFoundException e)
		{
			// Sodium (or this fork of it) isn't installed
			isReady = null;
			isBoxOccluded = null;
		}
		catch (Throwable t)
		{
			LOGGER.warn("Sodium's Hi-Z occlusion API couldn't be reached, LOD occlusion falls back to hardware queries: [" + t.getMessage() + "].");
			isReady = null;
			isBoxOccluded = null;
		}
	}

	/** Whether Sodium is present and has a Hi-Z buffer for the current camera. Render thread only. */
	public static boolean isReady()
	{
		if (!looked)
		{
			lookup();
		}

		if (isReady == null)
		{
			return false;
		}

		try
		{
			return (boolean) isReady.invokeExact();
		}
		catch (Throwable t)
		{
			return false;
		}
	}

	/** True only if the world-space box is certainly hidden behind near vanilla terrain. Render thread only. */
	public static boolean isBoxOccluded(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
	{
		if (isBoxOccluded == null)
		{
			return false;
		}

		try
		{
			return (boolean) isBoxOccluded.invokeExact(minX, minY, minZ, maxX, maxY, maxZ);
		}
		catch (Throwable t)
		{
			return false;
		}
	}

}
