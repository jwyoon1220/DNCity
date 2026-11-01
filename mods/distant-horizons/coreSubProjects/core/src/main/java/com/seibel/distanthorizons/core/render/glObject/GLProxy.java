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

package com.seibel.distanthorizons.core.render.glObject;

import com.seibel.distanthorizons.api.enums.config.EDhApiGLErrorHandlingMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.objects.GLMessages.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A singleton that holds references to different openGL contexts
 * and GPU capabilities.
 */
public class GLProxy
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererGLEventToFile)
			.chatLevelConfig(Config.Common.Logging.logRendererGLEventToChat)
			.build();
	
	public static final Set<String> LOGGED_GL_MESSAGES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	
	
	
	private static GLProxy instance = null;
	
	
	private final ConcurrentLinkedQueue<Runnable> renderThreadRunnableQueue = new ConcurrentLinkedQueue<>();
	
	/** Minecraft's GL capabilities */
	public final GLCapabilities glCapabilities;
	
	// DNCity: these used to be probed at runtime and gated legacy fallback paths (pre-GL4.3 vertex
	// attribute binding, pre-GL4.4 buffer upload, an ARB_instanced_arrays fallback for pre-GL3.3
	// contexts). The hard floor below is now GL4.4, which guarantees all of these, so they're
	// hardcoded true rather than probed -- the legacy branches they used to gate were deleted
	// (see VertexAttributePreGL43's removal, GenericObjectRenderer's ARB fallback removal, and
	// this constructor's simplified upload-method selection below).
	public final boolean bufferStorageSupported = true; // GL4.4, required by the floor check below
	public final boolean vertexAttributeBufferBindingSupported = true; // GL4.3, required by the floor check below
	public final boolean vertexAttribDivisorSupported = true; // GL3.3, required by the floor check below
	
	private final EDhApiGpuUploadMethod preferredUploadMethod;
	
	public final GLMessageBuilder vanillaDebugMessageBuilder = 
		new GLMessageBuilder(
			(type) ->
			{
				if (type == EGLMessageType.POP_GROUP)
					return false;
				else if (type == EGLMessageType.PUSH_GROUP)
					return false;
				else if (type == EGLMessageType.MARKER)
					return false;
				else
					return true;
			},
			(severity) ->
			{
				// notifications can generally be ignored (if they are logged at all)
				if (severity == EGLMessageSeverity.NOTIFICATION)
					return false;
				else
					return true;
			},
			null
	);
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private GLProxy() throws IllegalStateException
	{
		// this must be created on minecraft's render context to work correctly
		if (GLFW.glfwGetCurrentContext() == 0L)
		{
			throw new IllegalStateException(GLProxy.class.getSimpleName() + " was created outside the render thread!");
		}
		
		LOGGER.info("Creating " + GLProxy.class.getSimpleName() + "... If this is the last message you see there must have been an OpenGL error.");
		LOGGER.info("Lod Render OpenGL version [" + GL32.glGetString(GL32.GL_VERSION) + "].");
		
		
		
		
		//============================//
		// get Minecraft's GL context //
		//============================//
		
		// get Minecraft's capabilities
		this.glCapabilities = GL.getCapabilities();
		
		// DNCity: raised from OpenGL 3.2 -> 4.4. This used to be a soft floor with runtime
		// capability probing (glBufferStorage/glBindVertexBuffer/GL_ARB_instanced_arrays null
		// checks below) picking a legacy fallback path for anything below GL4.3/4.4. Since this
		// mod only ships on Windows (see AGENTS.md) and any GPU capable of running this pack's
		// Sodium+Iris+Flywheel+Veil stack already supports GL4.4, that fallback machinery was
		// dead weight -- GL4.4 (Buffer Storage + Vertex Attribute Buffer Binding) is now required
		// outright instead of being an optional "if available" upgrade.
		if (!this.glCapabilities.OpenGL44)
		{
			String supportedVersionInfo = this.getFailedVersionInfo(this.glCapabilities);
			
			// See full requirement at above.
			String errorMessage = ModInfo.READABLE_NAME + " was initializing " + GLProxy.class.getSimpleName()
					+ " and discovered this GPU doesn't meet the OpenGL requirements. Sorry I couldn't tell you sooner :(\n" +
					"Additional info:\n" + supportedVersionInfo;
			MC.crashMinecraft(errorMessage, new UnsupportedOperationException("Distant Horizon OpenGL requirements not met"));
		}
	 	LOGGER.info("minecraftGlCapabilities:\n" + this.versionInfoToString(this.glCapabilities));
		
		if (Config.Client.Advanced.Debugging.OpenGl.overrideVanillaGLLogger.get())
		{
			GLUtil.setupDebugMessageCallback(new PrintStream(new GLMessageOutputStream(GLProxy::logMessage, this.vanillaDebugMessageBuilder), true));
		}
		
		
		
		//======================//
		// get GPU capabilities //
		//======================//

		// DNCity: always Buffer Storage (GL4.4) -- the floor check above guarantees it's
		// available, so there's no vendor/OS-specific fallback selection to do anymore (this used
		// to branch NVIDIA/AMD/Intel to different fallback methods, and force macOS to the most
		// basic GL_DATA path regardless of capability; this repo is Windows-only, see AGENTS.md,
		// so that OS branch was always dead code here too).
		this.preferredUploadMethod = EDhApiGpuUploadMethod.BUFFER_STORAGE;
		LOGGER.info("Preferred upload method is [" + this.preferredUploadMethod + "].");
		
		
		
		//==========//
		// clean up //
		//==========//
		
		// GLProxy creation success
		LOGGER.info(GLProxy.class.getSimpleName() + " creation successful. OpenGL smiles upon you this day.");
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public static boolean hasInstance() { return instance != null; }
	/** @throws IllegalStateException if the Proxy hasn't been created yet and this is called outside the render thread */
	public static GLProxy getInstance() throws IllegalStateException
	{
		if (instance == null)
		{
			instance = new GLProxy();
		}
		
		return instance;
	}
	
	public EDhApiGpuUploadMethod getGpuUploadMethod() 
	{
		EDhApiGpuUploadMethod uploadOverride = Config.Client.Advanced.Debugging.OpenGl.glUploadMode.get();
		if (uploadOverride == EDhApiGpuUploadMethod.AUTO)
		{
			return this.preferredUploadMethod;
		}
		
		return uploadOverride;
	}
	
	public boolean runningOnRenderThread()
	{
		long currentContext = GLFW.glfwGetCurrentContext();
		return currentContext != 0L; // if the context isn't null, it's the MC context
	}
	
	
	
	//=========================//
	// Worker Thread Runnables //
	//=========================//
	
	public void queueRunningOnRenderThread(Runnable renderCall)
	{
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		this.renderThreadRunnableQueue.add(() -> this.runOpenGlCall(renderCall, stackTrace));
	}
	private void runOpenGlCall(Runnable renderCall, StackTraceElement[] stackTrace)
	{
		try
		{
			renderCall.run();
		}
		catch (Exception e)
		{
			RuntimeException error = new RuntimeException("Uncaught Exception during GL call execution:", e);
			error.setStackTrace(stackTrace);
			LOGGER.error("[" + Thread.currentThread().getName() + "] ran into an unexpected error running a GL call, Error: ["+ e.getMessage() +"].", error);
		}
	}
	
	/**
	 * Doesn't do any thread/GL Context validation.
	 * Running this outside of the render thread may cause crashes or other issues. 
	 */
	public void runRenderThreadTasks()
	{
		long startTime = System.nanoTime();
		
		Runnable runnable = this.renderThreadRunnableQueue.poll();
		while(runnable != null)
		{
			runnable.run();
			
			// only try running for 4ms (240 FPS) at a time to prevent random lag spikes
			long currentTime = System.nanoTime();
			long runDuration = currentTime - startTime;
			if (runDuration > 4_000_000)
			{
				break;
			}
			
			runnable = this.renderThreadRunnableQueue.poll();
		}
	}
	
	
	
	//=========//
	// logging //
	//=========//
	
	/** this method is called on the render thread at the point of the GL Error */
	private static void logMessage(GLMessage msg)
	{
		EDhApiGLErrorHandlingMode errorHandlingMode = Config.Client.Advanced.Debugging.OpenGl.glErrorHandlingMode.get();
		if (errorHandlingMode == EDhApiGLErrorHandlingMode.IGNORE)
		{
			return;
		}
		
		
		
		boolean onlyLogOnce = Config.Client.Advanced.Debugging.OpenGl.onlyLogGlErrorsOnce.get();
		String errorMessage = "GL ERROR [" + msg.id + "] from [" + msg.source + "]: [" + msg.message + "]"+(onlyLogOnce ? " this message will only be logged once" : "")+".";
		if (onlyLogOnce
			&& !LOGGED_GL_MESSAGES.add(errorMessage))
		{
			// this message has already been logged
			return;
		}
		
		
		// create an exception so we get a stacktrace of where the message was triggered from
		RuntimeException exception = new RuntimeException(errorMessage);
		
		if (msg.type == EGLMessageType.ERROR || msg.type == EGLMessageType.UNDEFINED_BEHAVIOR)
		{
			// critical error
			
			LOGGER.error(exception.getMessage(), exception);
			
			if (errorHandlingMode == EDhApiGLErrorHandlingMode.LOG_THROW)
			{
				// will probably crash the game,
				// good for quickly checking if there's a problem while preventing log spam
				throw exception;
			}
		}
		else
		{
			// non-critical log
			
			EGLMessageSeverity severity = msg.severity;
			if (severity == null)
			{
				// just in case the message was malformed
				severity = EGLMessageSeverity.LOW;
			}
			
			switch (severity)
			{
				case HIGH:
					LOGGER.error(exception.getMessage(), exception);
					break;
				case MEDIUM:
					LOGGER.warn(exception.getMessage(), exception);
					break;
				case LOW:
					LOGGER.info(exception.getMessage(), exception);
					break;
				case NOTIFICATION:
					LOGGER.debug(exception.getMessage(), exception);
					break;
			}
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private String getFailedVersionInfo(GLCapabilities c)
	{
		return "Your OpenGL support:\n" +
				"openGL version 4.4+: [" + c.OpenGL44 + "] <- REQUIRED\n" +
				"Vertex Attribute Buffer Binding: [" + (c.glVertexAttribBinding != 0) + "]\n" +
				"Buffer Storage: [" + (c.glBufferStorage != 0) + "]\n" +
				"If you noticed that your computer supports higher OpenGL versions"
				+ " but not the required version, try running the game in compatibility mode."
				+ " (How you turn that on, I have no clue~)";
	}

	private String versionInfoToString(GLCapabilities c)
	{
		return "Your OpenGL support:\n" +
				"openGL version 4.4+: [" + c.OpenGL44 + "] <- REQUIRED\n" +
				"Vertex Attribute Buffer Binding: [" + (c.glVertexAttribBinding != 0) + "]\n" +
				"Buffer Storage: [" + (c.glBufferStorage != 0) + "]\n";
	}
	
	
	
}
