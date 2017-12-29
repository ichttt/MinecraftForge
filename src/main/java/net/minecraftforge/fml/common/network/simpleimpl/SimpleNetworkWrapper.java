/*
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.common.network.simpleimpl;

import io.netty.channel.ChannelFutureListener;

import java.lang.reflect.Method;
import java.util.EnumMap;

import net.minecraft.util.IThreadListener;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.FMLEmbeddedChannel;
import net.minecraftforge.fml.common.network.FMLOutboundHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.relauncher.Side;

/**
 * This class is a simplified netty wrapper for those not wishing to deal with the full power of netty.
 * It provides a simple message driven system, based on a discriminator byte over the custom packet channel.
 * It assumes that you have a series of unique message types with each having a unique handler. Generally, context should be
 * derived at message handling time.
 *
 * Usage is simple:<ul>
 * <li>construct, and store, an instance of this class. It will automatically register and configure your underlying netty channel.
 *
 * <li>Then, call {@link #registerMessage(Class, Class, int, Side)} for each message type you want to exchange
 * providing an {@link IMessageHandler} implementation class as well as an {@link IMessage} implementation class. The side parameter
 * to that method indicates which side (server or client) the <em>message processing</em> will occur on. The discriminator byte
 * should be unique for this channelName - it is used to discriminate between different types of message that might
 * occur on this channel (a simple form of message channel multiplexing, if you will).
 * <li>To get a packet suitable for presenting to the rest of minecraft, you can call {@link #getPacketFrom(IMessage)}. The return result
 * is suitable for returning from things like {@link TileEntity#getUpdatePacket()} for example.
 * <li>Finally, use the sendXXX to send unsolicited messages to various classes of recipients.
 * </ul>
 *
 * Example
 * <code>
 * <pre>
 *  // Request message
 *  public Message1 implements IMessage {
 *  // message structure
 *   public fromBytes(ByteBuf buf) {
 *    // build message from byte array
 *   }
 *   public toBytes(ByteBuf buf) {
 *    // put message content into byte array
 *   }
 *  }
 *  // Reply message
 *  public Message2 implements IMessage {
 *   // stuff as before
 *  }
 *  // Message1Handler expects input of type Message1 and returns type Message2
 *  public Message1Handler implements IMessageHandler<Message1,Message2> {
 *   public Message2 onMessage(Message1 message, MessageContext ctx) {
 *    // do something and generate reply message
 *    ctx.reply(aMessage2Object);
 *   }
 *  }
 *  // Message2Handler expects input of type Message2 and returns no message (IMessage)
 *  public Message2Handler implements IMessageHandler<Message2,IMessage> {
 *   public IMessage onMessage(Message2 message, MessageContext ctx) {
 *    // handle the message 2 response message at the other end
 *    // no reply for this message - do nothing
 *   }
 *  }
 *
 *  // Code in a {@link FMLPreInitializationEvent} or {@link FMLInitializationEvent} handler
 *  SimpleNetworkWrapper wrapper = NetworkRegistry.newSimpleChannel("MYCHANNEL");
 *  // Message1 is handled by the Message1Handler class, it has discriminator id 1 and it's on the client
 *  wrapper.registerMessage(Message1Handler.class, Message1.class, 1, Side.CLIENT);
 *  // Message2 is handled by the Message2Handler class, it has discriminator id 2 and it's on the server
 *  wrapper.registerMessage(Message2Handler.class, Message2.class, 2, Side.SERVER);
 *  </pre>
 * </code>
 *
 * Note: As of Minecraft 1.8 packets are by default handled on the network thread.
 * That means that your {@link IMessageHandler} can not interact with most game objects directly.
 * Minecraft provides a convenient way to make your code execute on the main thread instead using {@link IThreadListener#addScheduledTask(Runnable)}.
 * The way to obtain an {@link IThreadListener} is using either the {@link net.minecraft.client.Minecraft} instance (client side) or a {@link net.minecraft.world.WorldServer} instance (server side).
 *
 * @author cpw
 *
 */
public class SimpleNetworkWrapper {
    private EnumMap<Side, FMLEmbeddedChannel> channels;
    private SimpleIndexedCodec packetCodec;
    private static Class<?> defaultChannelPipeline;
    private static Method generateName;
    static
    {
        try
        {
            defaultChannelPipeline = Class.forName("io.netty.channel.DefaultChannelPipeline");
            generateName = defaultChannelPipeline.getDeclaredMethod("generateName", ChannelHandler.class);
            generateName.setAccessible(true);
        }
        catch (Exception e)
        {
            // How is this possible?
            throw new RuntimeException("What? Netty isn't installed, what magic is this?", e);
        }
    }
    public SimpleNetworkWrapper(String channelName)
    {
        packetCodec = new SimpleIndexedCodec();
        channels = NetworkRegistry.INSTANCE.newChannel(channelName, packetCodec);
    }

    private String generateName(ChannelPipeline pipeline, ChannelHandler handler)
    {
        try
        {
            return (String)generateName.invoke(defaultChannelPipeline.cast(pipeline), handler);
        }
        catch (Exception e)
        {
            throw new RuntimeException("It appears we somehow have a not-standard pipeline. Huh", e);
        }
    }
    /**
     * Register a message and it's associated handler. The message will have the supplied discriminator byte. The message handler will
     * be registered on the supplied side (this is the side where you want the message to be processed and acted upon).
     *
     * @param messageHandler the message handler type
     * @param requestMessageType the message type
     * @param discriminator a discriminator byte
     * @param side the side for the handler
     */
    public <REQ extends IMessage> void registerMessage(Class<? extends IMessageHandler<REQ>> messageHandler, Class<REQ> requestMessageType, int discriminator, Side side)
    {
        registerMessage(instantiate(messageHandler), requestMessageType, discriminator, side);
    }
    
    static <REQ extends IMessage> IMessageHandler<? super REQ> instantiate(Class<? extends IMessageHandler<? super REQ>> handler)
    {
        try
        {
            return handler.newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Register a message and it's associated handler. The message will have the supplied discriminator byte. The message handler will
     * be registered on the supplied side (this is the side where you want the message to be processed and acted upon).
     *
     * @param messageHandler the message handler instance
     * @param requestMessageType the message type
     * @param discriminator a discriminator byte
     * @param side the side for the handler
     */
    public <REQ extends IMessage> void registerMessage(IMessageHandler<? super REQ> messageHandler, Class<REQ> requestMessageType, int discriminator, Side side)
    {
        packetCodec.addDiscriminator(discriminator, requestMessageType);
        FMLEmbeddedChannel channel = channels.get(side);
        String type = channel.findChannelHandlerNameForType(SimpleIndexedCodec.class);
        if (side == Side.SERVER)
        {
            addServerHandlerAfter(channel, type, messageHandler, requestMessageType);
        }
        else
        {
            addClientHandlerAfter(channel, type, messageHandler, requestMessageType);
        }
    }

    private <REQ extends IMessage> void addServerHandlerAfter(FMLEmbeddedChannel channel, String type, IMessageHandler<? super REQ> messageHandler, Class<REQ> requestType)
    {
        SimpleChannelHandlerWrapper<REQ> handler = getHandlerWrapper(messageHandler, Side.SERVER, requestType);
        channel.pipeline().addAfter(type, generateName(channel.pipeline(), handler), handler);
    }

    private <REQ extends IMessage> void addClientHandlerAfter(FMLEmbeddedChannel channel, String type, IMessageHandler<? super REQ> messageHandler, Class<REQ> requestType)
    {
        SimpleChannelHandlerWrapper<REQ> handler = getHandlerWrapper(messageHandler, Side.CLIENT, requestType);
        channel.pipeline().addAfter(type, generateName(channel.pipeline(), handler), handler);
    }

    private <REQ extends IMessage> SimpleChannelHandlerWrapper<REQ> getHandlerWrapper(IMessageHandler<? super REQ> messageHandler, Side side, Class<REQ> requestType)
    {
        return new SimpleChannelHandlerWrapper<>(messageHandler, side, requestType);
    }

    /**
     * Construct a minecraft packet from the supplied message. Can be used where minecraft packets are required, such as
     * {@link TileEntity#getUpdatePacket()}.
     *
     * @param message The message to translate into packet form
     * @return A minecraft {@link Packet} suitable for use in minecraft APIs
     */
    public Packet<?> getPacketFrom(IMessage message)
    {
        return channels.get(Side.SERVER).generatePacketFrom(message);
    }

    /**
     * Send this message to everyone.
     * The {@link IMessageHandler} for this message type should be on the CLIENT side.
     *
     * @param message The message to send
     */
    public void sendToAll(IMessage message)
    {
        channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALL);
        channels.get(Side.SERVER).writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    /**
     * Send this message to the specified player.
     * The {@link IMessageHandler} for this message type should be on the CLIENT side.
     *
     * @param message The message to send
     * @param player The player to send it to
     */
    public void sendTo(IMessage message, EntityPlayerMP player)
    {
        channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.PLAYER);
        channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(player);
        channels.get(Side.SERVER).writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    /**
     * Send this message to everyone within a certain range of a point.
     * The {@link IMessageHandler} for this message type should be on the CLIENT side.
     *
     * @param message The message to send
     * @param point The {@link TargetPoint} around which to send
     */
    public void sendToAllAround(IMessage message, NetworkRegistry.TargetPoint point)
    {
        channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALLAROUNDPOINT);
        channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(point);
        channels.get(Side.SERVER).writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    /**
     * Send this message to everyone within the supplied dimension.
     * The {@link IMessageHandler} for this message type should be on the CLIENT side.
     *
     * @param message The message to send
     * @param dimensionId The dimension id to target
     */
    public void sendToDimension(IMessage message, int dimensionId)
    {
        channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.DIMENSION);
        channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(dimensionId);
        channels.get(Side.SERVER).writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    /**
     * Send this message to the server.
     * The {@link IMessageHandler} for this message type should be on the SERVER side.
     *
     * @param message The message to send
     */
    public void sendToServer(IMessage message)
    {
        channels.get(Side.CLIENT).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.TOSERVER);
        channels.get(Side.CLIENT).writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }
}
