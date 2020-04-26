/*
 * Copyright (c) 2014-2020 by The Monix Connect Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.connect.hdfs

import java.io.File

import monix.eval.Task
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.apache.hadoop.hdfs.{HdfsConfiguration, MiniDFSCluster}
import monix.reactive.{Consumer, Observable}
import org.scalatest.concurrent.ScalaFutures
import monix.execution.Scheduler.Implicits.global

class HdfsSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures {

  private var miniHdfs: MiniDFSCluster = _
  private val dir = "./temp/hadoop"
  private val port: Int = 54310
  private val conf = new Configuration()
  conf.set("fs.default.name", s"hdfs://localhost:$port")
  val fs: FileSystem = FileSystem.get(conf)

  s"${Hdfs}" should {
    "write and read back a single chunk of bytes" in new HdfsFixture {
      //given
      val path: Path = new Path(genFileName.sample.get)
      val hdfsWriter: Consumer[Array[Byte], Task[Int]] = Hdfs.write(fs, path)
      val chunk: Array[Byte] = genChunk.sample.get

      //when
      val offset = Observable
        .pure(chunk)
        .consumeWith(hdfsWriter)
        .runSyncUnsafe()
        .runSyncUnsafe()

      //then
      val r: Array[Byte] = Hdfs.read(fs, path).headL.runSyncUnsafe()
      r shouldBe chunk
      offset shouldBe chunk.size
    }

  }

  override protected def beforeAll(): Unit = {
    val baseDir = new File(dir, "test")
    val miniDfsConf = new HdfsConfiguration
    miniDfsConf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, baseDir.getAbsolutePath)
    miniHdfs = new MiniDFSCluster.Builder(miniDfsConf)
      .nameNodePort(port)
      .format(true)
      .build()
    miniHdfs.waitClusterUp()
  }

  override protected def afterAll(): Unit = {
    fs.close()
    miniHdfs.shutdown()
  }

  override protected def afterEach(): Unit = {
    fs.delete(new Path(dir), true)
  }
}
