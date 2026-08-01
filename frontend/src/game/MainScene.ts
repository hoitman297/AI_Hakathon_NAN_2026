import Phaser from 'phaser'

export class MainScene extends Phaser.Scene {
  constructor() {
    super('MainScene')
  }

  create() {
    this.add.text(20, 20, 'Hello Phaser', {
      fontSize: '24px',
      color: '#ffffff',
    })
  }
}
