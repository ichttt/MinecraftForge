package net.minecraftforge.debug;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

@Mod(modid = NetworkBounceTest.MODID, name = "Network Bounce Test Mod", version = "1.0.0")
public class NetworkBounceTest
{
    private static final boolean ENABLED = false;
    public static final String MODID = "networkbouncetest";
    private static Logger logger;
    private static SimpleNetworkWrapper networking;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        if (ENABLED)
        {
            logger = event.getModLog();
            networking = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
            int i = 0;
            networking.registerMessage(MessageTestClientHandler.class, MessageTest.class, ++i, Side.CLIENT);
            networking.registerMessage(MessageTestServerHandler.class, MessageTest.class, ++i, Side.SERVER);
            logger.warn("Noisy NetworkBounceTest enabled!");
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Side.CLIENT)
    public static class ClientEventHandler
    {
        public static int ticks = 0;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event)
        {
            if (Minecraft.getMinecraft().world == null || !ENABLED)
                return;
            if (event.phase == TickEvent.Phase.END)
            {
                if (ticks < 20 && ticks != -1)
                {
                    ticks++;
                }
                else if (ticks != -1)
                {
                    logger.info("Starting pinging");
                    networking.sendToServer(new MessageTest(1));
                    ticks = -1;
                }
            }
        }

        @SubscribeEvent
        public static void playerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event)
        {
            ticks = 0;
        }

    }

    public static class MessageTest implements IMessage
    {
        private long ticks;
        public MessageTest()
        {
        }

        public MessageTest(long ticks)
        {
            this.ticks = ticks;
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            this.ticks = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            buf.writeLong(this.ticks);
        }
    }

    public static class MessageTestClientHandler implements IMessageHandler<MessageTest>
    {

        @Override
        public void onMessage(MessageTest message, MessageContext ctx)
        {
            if (message.ticks % 20 == 0) //verify reply can be used on main and network thread can call reply
            {
                Minecraft mc = Minecraft.getMinecraft();
                mc.addScheduledTask(() ->
                {
                    mc.player.sendMessage(new TextComponentString("Ticks: " + message.ticks));
                    ctx.reply(new MessageTest(++message.ticks));
                });
            }
            else
            {
                ctx.reply(new MessageTest(++message.ticks));
            }
        }
    }

    public static class MessageTestServerHandler implements IMessageHandler<MessageTest>
    {

        @Override
        public void onMessage(MessageTest message, MessageContext ctx)
        {
            if (message.ticks % 20 == 0) //verify reply can be used on main and network thread can call reply
            {
                MinecraftServer server = Preconditions.checkNotNull(ctx.getServerHandler().player.getServer());
                if (!server.isServerRunning())
                    return;
                server.addScheduledTask(() ->
                {
                    //noinspection ResultOfMethodCallIgnored
                    server.getPlayerList().getPlayers(); //Do something on the server thread
                    ctx.reply(new MessageTest(++message.ticks));
                });
            }
            else
            {
                ctx.reply(new MessageTest(++message.ticks));
            }
        }
    }
}
