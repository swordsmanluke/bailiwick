package threads.lite;

import static androidx.test.internal.util.Checks.checkArgument;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

class FileServer {


    static void insertRecord(@NonNull File file,
                             @NonNull Integer index,
                             @NonNull Integer packetSize,
                             @NonNull byte[] bytes) throws Exception {

        checkArgument(packetSize > 0);

        ByteBuffer data = ByteBuffer.allocate(packetSize);
        data.clear();
        checkArgument(file.exists());
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        FileChannel channel = randomAccessFile.getChannel();
        checkArgument(channel.isOpen());
        long pos = index * packetSize;
        channel.position(pos);
        checkArgument(pos == channel.position());

        try {
            data.put(bytes);

            data.flip();

            while (data.hasRemaining()) {
                channel.write(data);
            }
        } finally {
            data.clear();
            channel.close();
            randomAccessFile.close();
        }
    }


}
