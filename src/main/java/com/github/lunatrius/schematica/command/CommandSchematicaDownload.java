package com.github.lunatrius.schematica.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import org.apache.commons.io.FilenameUtils;

import com.github.lunatrius.schematica.FileFilterSchematic;
import com.github.lunatrius.schematica.Schematica;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import com.github.lunatrius.schematica.handler.DownloadHandler;
import com.github.lunatrius.schematica.network.transfer.SchematicTransfer;
import com.github.lunatrius.schematica.reference.Names;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.util.FileUtils;
import com.github.lunatrius.schematica.world.schematic.SchematicFormat;

public class CommandSchematicaDownload extends CommandSchematicaBase {

    private static final FileFilterSchematic FILE_FILTER_SCHEMATIC = new FileFilterSchematic(false);

    @Override
    public String getCommandName() {
        return Names.Command.Download.NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return Names.Command.Download.Message.USAGE;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            throw new CommandException(Names.Command.Download.Message.PLAYERS_ONLY);
        }

        final File directory = Schematica.proxy.getPlayerSchematicDirectory((EntityPlayer) sender, true);
        final File[] files = directory.listFiles(FILE_FILTER_SCHEMATIC);

        if (files != null) {
            final List<String> filenames = new ArrayList<>();

            for (File file : files) {
                filenames.add(FilenameUtils.removeExtension(file.getName()));
            }

            return getListOfStringsFromIterableMatchingLastWord(args, filenames);
        }

        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        if (!(sender instanceof EntityPlayerMP player)) {
            throw new CommandException(Names.Command.Download.Message.PLAYERS_ONLY);
        }

        final String filename;
        if (ConfigurationHandler.useSchematicplusFormat) {
            filename = args[0] + ".schemplus";
        } else {
            filename = args[0] + ".schematic";
        }

        final File directory = Schematica.proxy.getPlayerSchematicDirectory(player, true);
        if (!FileUtils.contains(directory, filename)) {
            Reference.logger.error("{} has tried to download the file {}", player.getDisplayName(), filename);
            throw new CommandException(Names.Command.Download.Message.DOWNLOAD_FAILED);
        }

        final ISchematic schematic = SchematicFormat.readFromFile(directory, filename);

        if (schematic != null) {
            DownloadHandler.INSTANCE.transferMap.put(player, new SchematicTransfer(schematic, filename));
            sender.addChatMessage(
                new ChatComponentTranslation(Names.Command.Download.Message.DOWNLOAD_STARTED, filename));
        } else {
            throw new CommandException(Names.Command.Download.Message.DOWNLOAD_FAILED);
        }
    }
}
