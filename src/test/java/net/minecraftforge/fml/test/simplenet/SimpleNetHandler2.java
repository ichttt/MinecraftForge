package net.minecraftforge.fml.test.simplenet;

import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SimpleNetHandler2 implements IMessageHandler<SimpleNetTestMessage2>
{
    @Override
    public void onMessage(SimpleNetTestMessage2 message, MessageContext context)
    {
    }

}
