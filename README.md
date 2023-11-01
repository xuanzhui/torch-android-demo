# torch-android-demo
torch android demo using torchscript

### demo中的模型测试用，不适合用于正式环境  

## FruitClassify 图像分类

普通模型需转化成 `jit` 能处理的格式  

```
model = load_model(model_path)
model.eval()
input_tensor = torch.rand(1, 3, 224, 224)

script_model = torch.jit.trace(model, input_tensor)
script_model.save("fruit_classifier.jit.pt")
```


## AppleDetectionApp 目标检测

本例模型通过 `ultralytics` （`yolov8`）训练，需转换成`torchscript`  

`yolo export model=apple_detection_best.pt format=torchscript imgsz=640 optimize=True`  
* `imgsz` 对应于 `train` 的 `imgsz` 参数  
* `optimize` 表示针对mobile优化

注意运行的输出信息：  
`PyTorch: starting from 'apple_detection_best.pt' with input shape (1, 3, 640, 640) BCHW and output shape(s) (1, 5, 8400) (6.0 MB)`  
此处output => (1, 5, 8400)  
  
对应于 `PrePostProcessor.java` 中的   
* `mOutputColumn` = 5  
* `mOutputRow` = 5 * 8400  
另外 `mInputWidth` 和 `mInputHeight` 也需要和 `imgsz` 相对应，表示在预测之前需要先对图片进行缩放  

注意，不同版本的yolo返回的数据格式可能是不一样的，`yolov8`此处返回的格式是  
`[x1, x2,...x8400, y1, y2,..., w1, w2,..., h1, h2,..., score1, score2,...]`  
x, y 是检测出来的区域的中心点坐标  

