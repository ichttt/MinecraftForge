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
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.fml.common.network.FMLOutboundHandler;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nonnull;

/**
 * Context for the {@link IMessageHandler}
 *
 * @author cpw
 *
 */
public class MessageContext {
    /**
     * The Channel Handler for this message.
     */
    private final ChannelHandlerContext ctx;
    /**
     * The {@link INetHandler} for this message. It could be a client or server handler, depending
     * on the {@link #side} received.
     */
    public final INetHandler netHandler;

    /**
     * The Side this message has been received on
     */
    public final Side side;

    MessageContext(ChannelHandlerContext ctx, INetHandler netHandler, Side side)
    {
        this.ctx = ctx;
        this.netHandler = netHandler;
        this.side = side;
    }

    public void reply(@Nonnull IMessage message)
    {
        ctx.channel().attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.REPLY);
        ctx.writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public NetHandlerPlayServer getServerHandler()
    {
        return (NetHandlerPlayServer) netHandler;
    }

    public NetHandlerPlayClient getClientHandler()
    {
        return (NetHandlerPlayClient) netHandler;
    }
}
